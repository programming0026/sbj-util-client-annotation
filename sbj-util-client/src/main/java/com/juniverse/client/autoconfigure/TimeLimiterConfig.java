package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The Interface TimeLimiterConfig.
 * Configuration for Resilience4j Time Limiter pattern.
 * Time limiter sets a maximum execution time for requests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TimeLimiterConfig {

    /**
     * Time limiter enabled.
     * @return true, if time limiter is enabled
     */
    boolean enabled() default false;

    /**
     * Time limiter timeout.
     * Maximum execution time in milliseconds before timeout.
     *
     * @return the timeout in milliseconds
     */
    long timeout() default 30000;
}
