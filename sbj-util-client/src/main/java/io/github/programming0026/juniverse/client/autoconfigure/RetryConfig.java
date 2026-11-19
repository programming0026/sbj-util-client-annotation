package io.github.programming0026.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Configuration of the Resilience4j Retry pattern.
 *
 * It automatically retries failed requests with a configurable wait and exponential backoff.
 * It is always active; which errors are retried is decided by {@code ErrorHandler.shouldRetry}.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryConfig {

    /**
     * Maximum number of attempts, including the first one (1 = no retries).
     *
     * @return the maximum number of attempts
     */
    int maxRetries() default 3;

    /**
     * Initial wait between retries, in milliseconds.
     *
     * @return the initial wait in milliseconds
     */
    long retryDelay() default 1000;

    /**
     * Multiplier of the exponential backoff.
     *
     * Each retry waits {@code retryDelay * multiplier^(attempt - 1)}.
     * For example, with retryDelay=1000 and multiplier=2.0: 1 s, 2 s, 4 s, 8 s...
     *
     * @return the exponential backoff multiplier
     */
    double exponentialBackoffMultiplier() default 2.0;

    /**
     * Whether non-idempotent requests (POST and PATCH) are retried the same way as idempotent ones.
     *
     * By default (false) they are only retried when the connection fails, that is, when it is certain that the
     * request did not reach the server. With true they are also retried after HTTP errors, timeouts or dropped
     * connections, which may duplicate the operation: use it only if the service treats them idempotently
     * (for example, with an idempotency key).
     *
     * @return true to retry POST and PATCH without restrictions
     */
    boolean retryNonIdempotent() default false;
}
