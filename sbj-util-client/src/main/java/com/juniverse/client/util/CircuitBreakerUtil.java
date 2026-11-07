package com.juniverse.client.util;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.exception.ErrorHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

import java.time.Duration;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Crea las instancias de Resilience4j (circuit breaker, retry, rate limiter y time limiter) de cada
 * cliente a partir de su anotación {@link RestHttpClient}.
 *
 * Las instancias se guardan en registros por nombre: pedir dos veces el mismo nombre devuelve la misma
 * instancia.
 */
public class CircuitBreakerUtil {

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
     * Crea (u obtiene) el circuit breaker del cliente, con ventana deslizante por número de llamadas.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @return el circuit breaker
     */
    public CircuitBreaker createCircuitBreaker(String name, RestHttpClient config) {
        var cbConfig = config.circuitBreaker();

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold((float) cbConfig.failureRateThreshold())
                .slidingWindowSize(cbConfig.slidingWindowSize())
                .minimumNumberOfCalls(cbConfig.minimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(cbConfig.waitDurationInOpenState()))
                .permittedNumberOfCallsInHalfOpenState(cbConfig.permittedNumberOfCallsInHalfOpenState())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return circuitBreakerRegistry.circuitBreaker(name, circuitBreakerConfig);
    }

    /**
     * Crea (u obtiene) el retry del cliente con backoff exponencial.
     *
     * Los errores HTTP se reintentan solo si {@link ErrorHandler#shouldRetry} lo permite; cualquier
     * otra excepción (por ejemplo, de E/S) siempre se reintenta.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @param errorHandler el manejador de errores que decide qué estados se reintentan
     * @return el retry
     */
    public Retry createRetry(String name, RestHttpClient config, ErrorHandler errorHandler) {
        var retryConf = config.retry();

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(retryConf.maxRetries())
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                        retryConf.retryDelay(),
                        retryConf.exponentialBackoffMultiplier()
                ))
                .retryOnException(throwable -> {
                    if (throwable instanceof HttpStatusCodeException httpException) {
                        return errorHandler.shouldRetry(httpException.getStatusCode());
                    }
                    return true;
                })
                .build();

        return retryRegistry.retry(name, retryConfig);
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
     * Crea (u obtiene) el time limiter del cliente; al vencer el tiempo cancela la tarea en curso.
     *
     * @param name el nombre del cliente
     * @param config la configuración del cliente
     * @return el time limiter
     */
    public TimeLimiter createTimeLimiter(String name, RestHttpClient config) {
        var tlConfig = config.timeLimiter();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(tlConfig.timeout()))
                .cancelRunningFuture(true)
                .build();

        return timeLimiterRegistry.timeLimiter(name, timeLimiterConfig);
    }
}
