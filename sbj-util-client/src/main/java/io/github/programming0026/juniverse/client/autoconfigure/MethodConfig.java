package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Settings of a client method that override those of the {@link RestHttpClient} only for that method; for
 * example, giving a download more time without loosening the limits of the rest of the calls. Anything not
 * specified is inherited from the client. The circuit breaker and the rate limiter remain those of the whole
 * client.
 *
 * <pre>{@code
 * @GetMapping("/reports/{id}")
 * @MethodConfig(readTimeout = 120_000, maxRetries = 1, timeLimiterTimeout = 0)
 * InputStream report(@PathVariable String id);
 * }</pre>
 *
 * The values are validated when the client is created.
 *
 * @author Java Universe
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodConfig {

    /**
     * Maximum time to wait for the response, in milliseconds, for this method (replaces
     * {@link RestHttpClient#readTimeout()}). With an {@code InputStream} return type it also governs each read
     * of the body. {@code -1} (the default) inherits the client's value; if specified it must be greater than
     * zero.
     *
     * @return the read timeout in milliseconds, or {@code -1} to inherit it
     */
    long readTimeout() default -1;

    /**
     * Total number of attempts for this method, the first one included (replaces
     * {@link RetryConfig#maxRetries()}; {@code 1} means no retries). The rest of the retry configuration (wait,
     * backoff, {@code retryNonIdempotent}) is inherited from the client. {@code -1} (the default) inherits the
     * client's value; if specified it must be at least {@code 1}.
     *
     * @return the total number of attempts, or {@code -1} to inherit it
     */
    int maxRetries() default -1;

    /**
     * Maximum time of each attempt, in milliseconds, for this method (replaces
     * {@link TimeLimiterConfig#timeout()}). With a value greater than zero the method has a time limit even if
     * the client does not have it enabled; {@code 0} removes the client's limit for this method; {@code -1} (the
     * default) inherits the client's value. If the method has a {@link #readTimeout()} greater than the limit
     * that applies to it, the limit cuts first.
     *
     * @return the maximum time in milliseconds, {@code 0} to remove it or {@code -1} to inherit it
     */
    long timeLimiterTimeout() default -1;
}
