package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The Interface CircuitBreakerConfig.
 * Configuration for Resilience 4j Circuit Breaker pattern.
 * Circuit breaker prevents cascading failures by stopping requests to a failing service.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreakerConfig {

    /**
     * Circuit breaker enabled.
     *
     * @return true, if circuit breaker is enabled
     */
    boolean enabled() default false;

    /**
     * Circuit breaker failure rate threshold.
     * Percentage of failures (0-100) that will open the circuit.
     *
     * @return the failure rate threshold
     */
    int failureRateThreshold() default 50;

    /**
     * Circuit breaker sliding window size.
     * Number of calls in the sliding window used to calculate failure rate.
     *
     * @return the sliding window size
     */
    int slidingWindowSize() default 100;

    /**
     * Circuit breaker minimum number of calls.
     * Minimum number of calls required before the circuit breaker can calculate the failure rate.
     *
     * @return the minimum number of calls
     */
    int minimumNumberOfCalls() default 10;

    /**
     * Circuit breaker wait duration in open state.
     * Time in milliseconds the circuit breaker should wait before transitioning from open to half-open.
     *
     * @return the wait duration in milliseconds
     */
    long waitDurationInOpenState() default 60000;

    /**
     * Circuit breaker permitted number of calls in half open state.
     * Number of calls allowed in half-open state to test if the service has recovered.
     *
     * @return the permitted number of calls in half-open state
     */
    int permittedNumberOfCallsInHalfOpenState() default 5;
}
