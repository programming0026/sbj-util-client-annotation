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
 * Creates the Resilience4j instances (circuit breaker, retry, rate limiter and time limiter) of each
 * client from its {@link RestHttpClient} annotation.
 * The instances are kept in registries by name: asking twice for the same name returns the same
 * instance.
 *
 * @author Java Universe
 */
public class CircuitBreakerUtil {

    /** Suffix of the retry name for non-idempotent requests. */
    private static final String NON_IDEMPOTENT_SUFFIX = ":non-idempotent";

    /** Circuit breaker registry. */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /** Retry registry. */
    private final RetryRegistry retryRegistry;

    /** Rate limiter registry. */
    private final RateLimiterRegistry rateLimiterRegistry;

    /** Time limiter registry. */
    private final TimeLimiterRegistry timeLimiterRegistry;

    /**
     * Creates the utility with registries holding the default configuration.
     */
    public CircuitBreakerUtil() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        this.timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
    }

    /**
     * Gets the circuit breaker registry (for example, to publish its metrics).
     *
     * @return the circuit breaker registry
     */
    public CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }

    /**
     * Gets the retry registry.
     *
     * @return the retry registry
     */
    public RetryRegistry getRetryRegistry() {
        return retryRegistry;
    }

    /**
     * Gets the rate limiter registry.
     *
     * @return the rate limiter registry
     */
    public RateLimiterRegistry getRateLimiterRegistry() {
        return rateLimiterRegistry;
    }

    /**
     * Gets the time limiter registry.
     *
     * @return the time limiter registry
     */
    public TimeLimiterRegistry getTimeLimiterRegistry() {
        return timeLimiterRegistry;
    }

    /**
     * Creates (or gets) the client's circuit breaker, with a count-based sliding window.
     * What counts as a failure: HTTP errors according to {@link ErrorHandler#shouldRecordAsFailure}; time
     * limiter timeouts and I/O errors always; other {@link ClientException}s (configuration or usage
     * errors) never, because they do not indicate that the service is failing.
     *
     * @param name the client name
     * @param config the client configuration
     * @param errorHandler the error handler that decides which HTTP statuses count as a failure
     * @return the circuit breaker
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
     * Indicates whether the exception is the time limiter's timeout ({@link ClientException} with a
     * {@link TimeoutException} as the cause).
     *
     * @param throwable the exception
     * @return true if it is a timeout
     */
    public static boolean isTimeout(Throwable throwable) {
        return throwable instanceof ClientException && throwable.getCause() instanceof TimeoutException;
    }

    /**
     * Creates (or gets) the client's retry for idempotent requests.
     *
     * @param name the client name
     * @param config the client configuration
     * @param errorHandler the error handler that decides which statuses are retried
     * @return the retry
     * @see #createRetry(String, RestHttpClient, ErrorHandler, boolean)
     */
    public Retry createRetry(String name, RestHttpClient config, ErrorHandler errorHandler) {
        return createRetry(name, config, errorHandler, true);
    }

    /**
     * Creates (or gets) the client's retry with exponential backoff.
     * Idempotent requests (GET, HEAD, OPTIONS, TRACE, PUT, DELETE): HTTP errors are retried only
     * if {@link ErrorHandler#shouldRetry} allows it; timeouts and I/O errors always. It is never
     * retried when the circuit is open ({@link CallNotPermittedException}), when the rate limiter
     * rejects the call ({@link RequestNotPermitted}) nor on other {@link ClientException}s (configuration or
     * usage errors).
     * Non-idempotent requests (POST, PATCH): in addition to the above, only connection failures are retried
     * (see {@link #isConnectionFailure(Throwable)}), because in those the request never reached the
     * server; retrying after an HTTP error, a read timeout or a dropped connection could
     * duplicate the operation. {@code RetryConfig.retryNonIdempotent = true} removes this restriction.
     *
     * @param name the client name
     * @param config the client configuration
     * @param errorHandler the error handler that decides which statuses are retried
     * @param idempotent whether the retry is for idempotent requests
     * @return the retry
     */
    public Retry createRetry(String name, RestHttpClient config, ErrorHandler errorHandler, boolean idempotent) {
        return createRetry(name, config, errorHandler, idempotent, config.retry().maxRetries());
    }

    /**
     * Same as {@link #createRetry(String, RestHttpClient, ErrorHandler, boolean)} but with its own total of
     * attempts, for methods with {@code @MethodConfig(maxRetries = ...)}; the rest of the retry configuration
     * is taken from the client. The {@code name} must differ from that of the client's retry.
     *
     * @param name the instance name
     * @param config the client configuration
     * @param errorHandler the error handler that decides which statuses are retried
     * @param idempotent whether the retry is for idempotent requests
     * @param maxAttempts the total number of attempts, the first one included
     * @return the retry
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
     * Indicates whether the exception is a failure to establish the connection, in which the request was never
     * sent: connection refused ({@link ConnectException}), connect timeout ({@link ConnectTimeoutException})
     * or timeout waiting for a free connection from the pool ({@link ConnectionRequestTimeoutException}). The
     * whole cause chain is checked, because Spring wraps them in {@code ResourceAccessException}.
     *
     * @param throwable the exception
     * @return true if the request was never sent
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
     * Creates (or gets) the client's rate limiter.
     *
     * @param name the client name
     * @param config the client configuration
     * @return the rate limiter
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
     * Creates (or gets) the client's time limiter; when the time expires it cancels the running task.
     *
     * @param name the client name
     * @param config the client configuration
     * @return the time limiter
     */
    public TimeLimiter createTimeLimiter(String name, RestHttpClient config) {
        return createTimeLimiter(name, config.timeLimiter().timeout());
    }

    /**
     * Creates (or gets) a time limiter with its own time, for methods with
     * {@code @MethodConfig(timeLimiterTimeout = ...)}. The {@code name} must differ from that of the client's
     * time limiter.
     *
     * @param name the instance name
     * @param timeoutMillis the maximum time of each attempt, in milliseconds
     * @return the time limiter
     */
    public TimeLimiter createTimeLimiter(String name, long timeoutMillis) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMillis))
                .cancelRunningFuture(true)
                .build();

        return timeLimiterRegistry.timeLimiter(name, timeLimiterConfig);
    }
}
