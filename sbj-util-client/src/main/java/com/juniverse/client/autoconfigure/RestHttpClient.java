package com.juniverse.client.autoconfigure;


import com.juniverse.client.enums.ClientType;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.exception.ErrorHandler;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The Interface RestHttpClient.
 *
 * Defines a declarative REST HTTP client with built-in resilience patterns.
 * Supports circuit breaker, retry, rate limiting, and time limiting out of the box.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RestHttpClient {

    /**
     * Name.
     *
     * Unique identifier for this client (used for metrics and logging).
     *
     * @return the client name
     */
    String name() default "";

    /**
     * Url.
     *
     * Base URL for the REST service.
     *
     * @return the base URL
     */
    String url() default "";

    /**
     * Type.
     *
     * Client implementation type (REST_CLIENT or REST_TEMPLATE).
     *
     * @return the client type
     */
    ClientType type() default ClientType.REST_CLIENT;

    /**
     * Connect timeout.
     *
     * Connection timeout in milliseconds.
     *
     * @return the connect timeout
     */
    long connectTimeout() default 5000;

    /**
     * Read timeout.
     *
     * Read timeout in milliseconds.
     *
     * @return the read timeout
     */
    long readTimeout() default 30000;

    /**
     * Max connections total.
     *
     * Maximum total connections in the pool.
     *
     * @return the max connections
     */
    int maxConnections() default 100;

    /**
     * Max connections per route.
     *
     * Maximum connections per route in the pool.
     *
     * @return the max connections per route
     */
    int maxConnectionsPerRoute() default 20;

    /**
     * Connection time to live.
     *
     * Time to live for connections in milliseconds.
     * Default: -1 (no limit)
     *
     * @return the connection time to live
     */
    long connectionTimeToLive() default -1;

    /**
     * SSL configuration.
     *
     * Configure SSL/TLS settings including keystore and truststore.
     *
     * @return the SSL config
     */
    SSLConfig ssl() default @SSLConfig;

    /**
     * Circuit breaker configuration.
     *
     * Configure circuit breaker pattern for resilience.
     *
     * @return the circuit breaker config
     */
    CircuitBreakerConfig circuitBreaker() default @CircuitBreakerConfig;

    /**
     * Retry configuration.
     *
     * Configure retry pattern with exponential backoff.
     *
     * @return the retry config
     */
    RetryConfig retry() default @RetryConfig;

    /**
     * Rate limiter configuration.
     *
     * Configure rate limiting to control request throughput.
     *
     * @return the rate limiter config
     */
    RateLimiterConfig rateLimiter() default @RateLimiterConfig;

    /**
     * Time limiter configuration.
     *
     * Configure maximum execution time for requests.
     *
     * @return the time limiter config
     */
    TimeLimiterConfig timeLimiter() default @TimeLimiterConfig;

    /**
     * Error handler.
     *
     * Custom error handler for HTTP error responses.
     *
     * @return the error handler class
     */
    Class<? extends ErrorHandler> errorHandler() default DefaultErrorHandler.class;
}
