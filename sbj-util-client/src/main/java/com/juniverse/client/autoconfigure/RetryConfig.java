package com.juniverse.client.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The Interface RetryConfig.
 *
 * Configuration for Resilience 4j Retry pattern.
 * Retry automatically retries failed requests with configurable delay and backoff.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryConfig {

    /**
     * Max retries.
     *
     * Maximum number of retry attempts.
     *
     * @return the maximum number of retries
     */
    int maxRetries() default 3;

    /**
     * Retry delay.
     *
     * Initial delay between retries in milliseconds.
     *
     * @return the retry delay in milliseconds
     */
    long retryDelay() default 1000;

    /**
     * Retry exponential backoff multiplier.
     *
     * Multiplier for exponential backoff. Each retry will wait retryDelay * (multiplier ^ attemptNumber).
     * For example, with delay=1000 and multiplier=2.0: 1s, 2s, 4s, 8s...
     *
     * @return the exponential backoff multiplier
     */
    double exponentialBackoffMultiplier() default 2.0;
}
