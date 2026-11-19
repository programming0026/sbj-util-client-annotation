package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuration of the Resilience4j Circuit Breaker pattern.
 *
 * The circuit breaker prevents cascading failures by no longer sending requests to a service that is failing.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreakerConfig {

    /**
     * Whether the circuit breaker is enabled.
     *
     * @return true if the circuit breaker is enabled
     */
    boolean enabled() default false;

    /**
     * Failure percentage (0-100) from which the circuit opens.
     *
     * @return the failure rate threshold
     */
    int failureRateThreshold() default 50;

    /**
     * Number of calls in the sliding window used to compute the failure rate.
     *
     * @return the sliding window size
     */
    int slidingWindowSize() default 100;

    /**
     * Minimum number of calls before the circuit breaker can compute the failure rate.
     *
     * @return the minimum number of calls
     */
    int minimumNumberOfCalls() default 10;

    /**
     * Time in milliseconds that the circuit stays open before moving to half-open.
     *
     * @return the wait time in the open state, in milliseconds
     */
    long waitDurationInOpenState() default 60000;

    /**
     * Number of calls allowed in the half-open state to check whether the service has recovered.
     *
     * @return the number of calls allowed in the half-open state
     */
    int permittedNumberOfCallsInHalfOpenState() default 5;
}
