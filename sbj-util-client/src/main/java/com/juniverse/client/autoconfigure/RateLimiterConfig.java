package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The Interface RateLimiterConfig.
 *
 * Configuration for Resilience4j Rate Limiter pattern.
 * Rate limiter controls the number of requests allowed in a given time period.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiterConfig {

    /**
     * Rate limiter enabled.
     *
     * @return true, if rate limiter is enabled
     */
    boolean enabled() default false;

    /**
     * Rate limiter limit for period.
     *
     * Maximum number of requests allowed per refresh period.
     *
     * @return the limit for period
     */
    int limitForPeriod() default 100;

    /**
     * Rate limiter limit refresh period.
     *
     * Period in milliseconds during which the limit applies.
     *
     * @return the limit refresh period in milliseconds
     */
    long limitRefreshPeriod() default 1000;

    /**
     * Rate limiter timeout duration.
     *
     * Maximum time in milliseconds a thread will wait for permission.
     *
     * @return the timeout duration in milliseconds
     */
    long timeoutDuration() default 5000;
}
