package io.github.programming0026.juniverse.client.util;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.exception.ErrorHandler;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Crea las instancias de Resilience4j (circuit breaker, retry, rate limiter y time limiter) de cada
 * cliente a partir de su anotación {@link RestHttpClient}.
 * Las instancias se guardan en registros por nombre: pedir dos veces el mismo nombre devuelve la misma
 * instancia.
 *
 * @author Java Universe
 */
public class CircuitBreakerUtil {

    /** Sufijo del nombre del retry para peticiones no idempotentes. */
    private static final String NON_IDEMPOTENT_SUFFIX = ":non-idempotent";

    /** Registro de circuit breakers. */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /** Registro de retries. */
    private final RetryRegistry retryRegistry;

    /** Registro de rate limiters. */
    private final RateLimiterRegistry rateLimiterRegistry;

    /** Registro de time limiters. */
    private final TimeLimiterRegistry timeLimiterRegistry;

    /**
     * Crea la utilidad con registros con la configuración por defecto.
     */
    public CircuitBreakerUtil() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        this.timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
    }

    /**
     * Obtiene el registro de circuit breakers (por ejemplo, para publicar sus métricas).
     *
     * @return el registro de circuit breakers
     */
    public CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }

    /**
     * Obtiene el registro de retries.
     *
     * @return el registro de retries
     */
    public RetryRegistry getRetryRegistry() {
        return retryRegistry;
    }

    /**
     * Obtiene el registro de rate limiters.
     *
     * @return el registro de rate limiters
     */
    public RateLimiterRegistry getRateLimiterRegistry() {
        return rateLimiterRegistry;
    }

    /**
     * Obtiene el registro de time limiters.
     *
     * @return el registro de time limiters
     */
    public TimeLimiterRegistry getTimeLimiterRegistry() {
        return timeLimiterRegistry;
    }

    /**
     * Crea (u obtiene) el circuit breaker del cliente, con ventana deslizante por número de llamadas.
     * Qué cuenta como falla: los errores HTTP según {@link ErrorHandler#shouldRecordAsFailure}; los
     * timeouts del timelimiter y los errores de E/S siempre; los demás {@link ClientException} (errores
     * de configuración o de uso) nunca, porque no indican que el servicio esté fallando.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @param errorHandler el manejador de errores que decide qué estados HTTP cuentan como falla
     * @return el circuit breaker
     */
    public CircuitBreaker createCircuitBreaker(String name, RestHttpClient config, ErrorHandler errorHandler) {
        var cbConfig = config.circuitBreaker();

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold((float) cbConfig.failureRateThreshold())
                .slidingWindowSize(cbConfig.slidingWindowSize())
                .minimumNumberOfCalls(cbConfig.minimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(cbConfig.waitDurationInOpenState()))
                .permittedNumberOfCallsInHalfOpenState(cbConfig.permittedNumberOfCallsInHalfOpenState())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(throwable -> {
                    if (throwable instanceof HttpStatusCodeException httpException) {
                        return errorHandler.shouldRecordAsFailure(httpException.getStatusCode());
                    }
                    if (throwable instanceof ClientException) {
                        return isTimeout(throwable);
                    }
                    return true;
                })
                .build();

        return circuitBreakerRegistry.circuitBreaker(name, circuitBreakerConfig);
    }

    /**
     * Indica si la excepción es el timeout del timelimiter ({@link ClientException} con un
     * {@link TimeoutException} como causa).
     *
     * @param throwable la excepción
     * @return true si es un timeout
     */
    public static boolean isTimeout(Throwable throwable) {
        return throwable instanceof ClientException && throwable.getCause() instanceof TimeoutException;
    }

    /**
     * Crea (u obtiene) el retry del cliente para peticiones idempotentes.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @param errorHandler el manejador de errores que decide qué estados se reintentan
     * @return el retry
     * @see #createRetry(String, RestHttpClient, ErrorHandler, boolean)
     */
    public Retry createRetry(String name, RestHttpClient config, ErrorHandler errorHandler) {
        return createRetry(name, config, errorHandler, true);
    }

    /**
     * Crea (u obtiene) el retry del cliente con backoff exponencial.
     * Peticiones idempotentes (GET, HEAD, OPTIONS, TRACE, PUT, DELETE): los errores HTTP se reintentan solo
     * si {@link ErrorHandler#shouldRetry} lo permite; los timeouts y los errores de E/S siempre. Nunca se
     * reintenta cuando el circuito está abierto ({@link CallNotPermittedException}), cuando el rate limiter
     * rechaza la llamada ({@link RequestNotPermitted}) ni ante otros {@link ClientException} (errores de
     * configuración o de uso).
     * Peticiones no idempotentes (POST, PATCH): además de lo anterior, solo se reintentan las fallas de
     * conexión (ver {@link #isConnectionFailure(Throwable)}), porque en ellas la petición nunca llegó al
     * servidor; reintentar tras un error HTTP, un timeout de lectura o un corte de conexión podría
     * duplicar la operación. {@code RetryConfig.retryNonIdempotent = true} quita esta restricción.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @param errorHandler el manejador de errores que decide qué estados se reintentan
     * @param idempotent si el retry es para peticiones idempotentes
     * @return el retry
     */
    public Retry createRetry(String name, RestHttpClient config, ErrorHandler errorHandler, boolean idempotent) {
        return createRetry(name, config, errorHandler, idempotent, config.retry().maxRetries());
    }

    /**
     * Igual que {@link #createRetry(String, RestHttpClient, ErrorHandler, boolean)} pero con un total de
     * intentos propio, para los métodos con {@code @MethodConfig(maxRetries = ...)}; el resto de la configuración
     * de reintentos se toma del cliente. Él {@code name} debe ser distinto al del retry del cliente.
     *
     * @param name el nombre de la instancia
     * @param config la configuración del cliente
     * @param errorHandler el manejador de errores que decide qué estados se reintentan
     * @param idempotent si el retry es para peticiones idempotentes
     * @param maxAttempts el total de intentos, el primero incluido
     * @return el retry
     */
    public Retry createRetry(String name, RestHttpClient config, ErrorHandler errorHandler, boolean idempotent,
                             int maxAttempts) {
        var retryConf = config.retry();

        Predicate<Throwable> retryable = throwable -> {
            if (throwable instanceof HttpStatusCodeException httpException) {
                return errorHandler.shouldRetry(httpException.getStatusCode());
            }
            if (throwable instanceof CallNotPermittedException || throwable instanceof RequestNotPermitted) {
                return false;
            }
            if (throwable instanceof ClientException) {
                return isTimeout(throwable);
            }
            return true;
        };
        boolean restricted = !idempotent && !retryConf.retryNonIdempotent();
        Predicate<Throwable> predicate = restricted
                ? throwable -> retryable.test(throwable) && isConnectionFailure(throwable)
                : retryable;

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                        retryConf.retryDelay(),
                        retryConf.exponentialBackoffMultiplier()
                ))
                .retryOnException(predicate)
                .build();

        return retryRegistry.retry(idempotent ? name : name + NON_IDEMPOTENT_SUFFIX, retryConfig);
    }

    /**
     * Indica si la excepción es una falla al establecer la conexión, en la que la petición nunca se envió:
     * conexión rechazada ({@link ConnectException}), timeout de conexión ({@link ConnectTimeoutException})
     * o timeout esperando una conexión libre del pool ({@link ConnectionRequestTimeoutException}). Se
     * revisa toda la cadena de causas, porque Spring las envuelve en {@code ResourceAccessException}.
     *
     * @param throwable la excepción
     * @return true si la petición no llegó a enviarse
     */
    public static boolean isConnectionFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ConnectException
                    || current instanceof ConnectTimeoutException
                    || current instanceof ConnectionRequestTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Crea (u obtiene) el rate limiter del cliente.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @return el rate limiter
     */
    public RateLimiter createRateLimiter(String name, RestHttpClient config) {
        var rlConfig = config.rateLimiter();

        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(rlConfig.limitForPeriod())
                .limitRefreshPeriod(Duration.ofMillis(rlConfig.limitRefreshPeriod()))
                .timeoutDuration(Duration.ofMillis(rlConfig.timeoutDuration()))
                .build();

        return rateLimiterRegistry.rateLimiter(name, rateLimiterConfig);
    }

    /**
     * Crea (u obtiene) él time limiter del cliente; al vencer el tiempo cancela la tarea en curso.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @return él time limiter
     */
    public TimeLimiter createTimeLimiter(String name, RestHttpClient config) {
        return createTimeLimiter(name, config.timeLimiter().timeout());
    }

    /**
     * Crea (u obtiene) un time limiter con un tiempo propio, para los métodos con
     * {@code @MethodConfig(timeLimiterTimeout = ...)}. Él {@code name} debe ser distinto al del timelimiter del
     * cliente.
     *
     * @param name el nombre de la instancia
     * @param timeoutMillis el tiempo máximo de cada intento, en milisegundos
     * @return él time limiter
     */
    public TimeLimiter createTimeLimiter(String name, long timeoutMillis) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMillis))
                .cancelRunningFuture(true)
                .build();

        return timeLimiterRegistry.timeLimiter(name, timeLimiterConfig);
    }
}
