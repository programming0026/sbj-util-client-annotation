package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuration of the Resilience4j Time Limiter pattern.
 *
 * It sets a maximum execution time for requests. If it is exceeded, the call throws
 * {@code ClientException} with a {@code TimeoutException} as the cause.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TimeLimiterConfig {

    /**
     * Whether the time limiter is enabled.
     *
     * @return true if the time limiter is enabled
     */
    boolean enabled() default false;

    /**
     * Maximum execution time in milliseconds.
     *
     * @return the maximum time in milliseconds
     */
    long timeout() default 30000;
}
