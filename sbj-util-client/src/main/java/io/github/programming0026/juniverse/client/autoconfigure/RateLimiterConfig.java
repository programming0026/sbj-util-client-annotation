package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuration of the Resilience4j Rate Limiter pattern.
 *
 * It limits the number of requests allowed in a period of time.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiterConfig {

    /**
     * Whether the rate limiter is enabled.
     *
     * @return true if the rate limiter is enabled
     */
    boolean enabled() default false;

    /**
     * Maximum number of requests allowed per refresh period.
     *
     * @return the limit per period
     */
    int limitForPeriod() default 100;

    /**
     * Duration in milliseconds of the period to which the limit applies.
     *
     * @return the refresh period in milliseconds
     */
    long limitRefreshPeriod() default 1000;

    /**
     * Maximum time in milliseconds that a thread waits to obtain a permit.
     *
     * @return the wait time in milliseconds
     */
    long timeoutDuration() default 5000;
}
