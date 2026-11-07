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
 * The Class Resilience4jConfiguration.
 */
public class CircuitBreakerUtil {
    
    /** The circuit breaker registry. */
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    /** The retry registry. */
    private final RetryRegistry retryRegistry;
    
    /** The rate limiter registry. */
    private final RateLimiterRegistry rateLimiterRegistry;
    
    /** The time limiter registry. */
    private final TimeLimiterRegistry timeLimiterRegistry;

    /**
     * Instantiates a new resilience 4 j configuration.
     */
    public CircuitBreakerUtil() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        this.timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
    }

    /**
     * Creates the circuit breaker.
     *
     * @param name the name
     * @param config the config
     * @return the circuit breaker
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
     * Creates the retry.
     *
     * @param name the name
     * @param config the config
     * @param errorHandler the error handler
     * @return the retry
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
     * Creates the rate limiter.
     *
     * @param name the name
     * @param config the config
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
     * Creates the time limiter.
     *
     * @param name the name
     * @param config the config
     * @return the time limiter
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
