package com.juniverse.client.util;

import com.juniverse.client.autoconfigure.CircuitBreakerConfig;
import com.juniverse.client.autoconfigure.RateLimiterConfig;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.autoconfigure.TimeLimiterConfig;
import com.juniverse.client.exception.DefaultErrorHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerUtilTest {

    @RestHttpClient(
            circuitBreaker = @CircuitBreakerConfig(enabled = true, failureRateThreshold = 30,
                    slidingWindowSize = 20, minimumNumberOfCalls = 5, waitDurationInOpenState = 1500,
                    permittedNumberOfCallsInHalfOpenState = 2),
            retry = @RetryConfig(maxRetries = 4, retryDelay = 10, exponentialBackoffMultiplier = 1.5),
            rateLimiter = @RateLimiterConfig(enabled = true, limitForPeriod = 7, limitRefreshPeriod = 250,
                    timeoutDuration = 100),
            timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 750))
    interface CustomClient {
    }

    private final CircuitBreakerUtil util = new CircuitBreakerUtil();

    private final RestHttpClient config = CustomClient.class.getAnnotation(RestHttpClient.class);

    @Test
    void createCircuitBreakerMapsAnnotationValues() {
        CircuitBreaker cb = util.createCircuitBreaker("cb", config);
        var cfg = cb.getCircuitBreakerConfig();

        assertThat(cb.getName()).isEqualTo("cb");
        assertThat(cfg.getFailureRateThreshold()).isEqualTo(30f);
        assertThat(cfg.getSlidingWindowSize()).isEqualTo(20);
        assertThat(cfg.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(cfg.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(cfg.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        assertThat(cfg.getSlidingWindowType())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
    }

    @Test
    void createRetryMapsAnnotationValues() {
        Retry retry = util.createRetry("retry", config, new DefaultErrorHandler());
        var cfg = retry.getRetryConfig();

        assertThat(cfg.getMaxAttempts()).isEqualTo(4);
        assertThat(cfg.getIntervalBiFunction().apply(1, null)).isEqualTo(10L);
        assertThat(cfg.getIntervalBiFunction().apply(2, null)).isEqualTo(15L);
    }

    @Test
    void retryPredicateDelegatesHttpStatusToErrorHandler() {
        Retry retry = util.createRetry("retry", config, new DefaultErrorHandler());
        Predicate<Throwable> predicate = retry.getRetryConfig().getExceptionPredicate();

        assertThat(predicate.test(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))).isTrue();
        assertThat(predicate.test(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))).isFalse();
        assertThat(predicate.test(new HttpClientErrorException(HttpStatus.NOT_FOUND))).isFalse();
    }

    @Test
    void retryPredicateRetriesNonHttpExceptions() {
        Retry retry = util.createRetry("retry", config, new DefaultErrorHandler());

        assertThat(retry.getRetryConfig().getExceptionPredicate().test(new IllegalStateException())).isTrue();
    }

    @Test
    void createRateLimiterMapsAnnotationValues() {
        RateLimiter rl = util.createRateLimiter("rl", config);
        var cfg = rl.getRateLimiterConfig();

        assertThat(cfg.getLimitForPeriod()).isEqualTo(7);
        assertThat(cfg.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(250));
        assertThat(cfg.getTimeoutDuration()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void createTimeLimiterMapsAnnotationValues() {
        TimeLimiter tl = util.createTimeLimiter("tl", config);

        assertThat(tl.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofMillis(750));
        assertThat(tl.getTimeLimiterConfig().shouldCancelRunningFuture()).isTrue();
    }

    @Test
    void sameNameReturnsCachedInstanceFromRegistry() {
        assertThat(util.createCircuitBreaker("same", config)).isSameAs(util.createCircuitBreaker("same", config));
    }
}
