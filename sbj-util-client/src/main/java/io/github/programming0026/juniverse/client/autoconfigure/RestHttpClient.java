package io.github.programming0026.juniverse.client.autoconfigure;


import io.github.programming0026.juniverse.client.config.JsonMapperConfigurer;
import io.github.programming0026.juniverse.client.enums.ClientType;
import io.github.programming0026.juniverse.client.exception.DefaultErrorHandler;
import io.github.programming0026.juniverse.client.exception.ErrorHandler;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Declares an HTTP REST client with built-in resilience patterns.
 *
 * It is placed on an interface whose methods use the Spring Web annotations ({@code @GetMapping},
 * {@code @PathVariable}, etc.). It includes circuit breaker, retry, rate limiting and time limiting.
 *
 * @author Java Universe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RestHttpClient {

    /**
     * Unique identifier of the client. It is used as the bean name and as the name of the Resilience4j
     * instances. If empty, the name of the interface is used.
     *
     * @return the client name
     */
    String name() default "";

    /**
     * Base URL of the REST service.
     *
     * @return the base URL
     */
    String url() default "";

    /**
     * HTTP implementation to use (REST_CLIENT or REST_TEMPLATE).
     *
     * @return the client type
     */
    ClientType type() default ClientType.REST_CLIENT;

    /**
     * Connection timeout, in milliseconds. It limits both the establishment of the TCP connection
     * and the wait to obtain a free connection from the pool.
     *
     * @return the connection timeout
     */
    long connectTimeout() default 5000;

    /**
     * Read timeout, in milliseconds: how long to wait for the response (and for each socket read) once
     * connected.
     *
     * @return the read timeout
     */
    long readTimeout() default 30000;

    /**
     * Maximum number of connections in the pool.
     *
     * @return the maximum number of connections
     */
    int maxConnections() default 100;

    /**
     * Maximum number of connections per route in the pool.
     *
     * @return the maximum number of connections per route
     */
    int maxConnectionsPerRoute() default 20;

    /**
     * Time to live of the connections, in milliseconds. Default -1 (no limit).
     *
     * @return the time to live of the connections
     */
    long connectionTimeToLive() default -1;

    /**
     * SSL/TLS configuration (keystore and truststore).
     *
     * @return the SSL configuration
     */
    SSLConfig ssl() default @SSLConfig;

    /**
     * Whether the client follows the server's redirects (301, 302, 303, 307 and 308). With {@code false} the
     * 3xx response is delivered as is: it can be read with a {@code ResponseEntity<T>} return type and its
     * {@code Location} header.
     *
     * @return true to follow redirects
     */
    boolean followRedirects() default true;

    /**
     * Maximum number of redirects followed in a single request; when exceeded the request fails. It only
     * applies if {@link #followRedirects()} is {@code true}.
     *
     * @return the maximum number of redirects, at least 1
     */
    int maxRedirects() default 50;

    /**
     * HTTP/HTTPS proxy through which the requests go out. Disabled while its {@code host} is empty.
     *
     * @return the proxy configuration
     */
    ProxyConfig proxy() default @ProxyConfig;

    /**
     * Circuit breaker configuration.
     *
     * @return the circuit breaker configuration
     */
    CircuitBreakerConfig circuitBreaker() default @CircuitBreakerConfig;

    /**
     * Retry configuration with exponential backoff.
     *
     * @return the retry configuration
     */
    RetryConfig retry() default @RetryConfig;

    /**
     * Rate limiter configuration, to control the volume of requests.
     *
     * @return the rate limiter configuration
     */
    RateLimiterConfig rateLimiter() default @RateLimiterConfig;

    /**
     * Configuration of the maximum execution time of the requests.
     *
     * @return the time limiter configuration
     */
    TimeLimiterConfig timeLimiter() default @TimeLimiterConfig;

    /**
     * Error handler for HTTP error responses. If a bean of that type exists it is used; otherwise,
     * Spring creates the instance with dependency injection (it can receive beans through the constructor).
     *
     * @return the error handler class
     */
    Class<? extends ErrorHandler> errorHandler() default DefaultErrorHandler.class;

    /**
     * Interceptors of this client (for example, authentication or logging), in the order in which they are
     * applied. Like the error handler: if a bean of that type exists it is used; otherwise Spring creates the
     * instance with dependency injection. They are added after the application's global interceptors
     * ({@code RestClientCustomizer}/{@code RestTemplateBuilder}).
     *
     * @return the interceptor classes
     */
    Class<? extends ClientHttpRequestInterceptor>[] interceptors() default {};

    /**
     * Logs every HTTP request (method, URL, status and duration) in the logger
     * {@code <library package>.http.<client name>} (see {@code Constants.BASE}), at INFO level,
     * or WARN if it fails. It never logs headers or bodies, and of the query only the parameter names. It is
     * applied before the {@link #interceptors()}, so the duration includes whatever they do.
     *
     * @return true to enable request logging
     */
    boolean logging() default false;

    /**
     * JSON configurer (Jackson 3) of this client, which starts from the application's {@code JsonMapper} and
     * changes only what it declares (naming strategy, dates, unknown properties...). The
     * {@link JsonMapperConfigurer} interface means there is no custom configuration. Like the error handler:
     * if a bean of that type exists it is used; otherwise Spring creates the instance with dependency
     * injection.
     *
     * @return the configurer class
     */
    Class<? extends JsonMapperConfigurer> jsonConfig() default JsonMapperConfigurer.class;

    /**
     * Class that answers when the service is unavailable. It must implement the client interface and
     * be concrete; each method of the class serves the interface method with the same signature. It is used
     * when, once the retries are exhausted, the call fails because of unavailability: {@code 5xx} response,
     * network error, timeout, open circuit or request limit. {@code 4xx} errors and usage errors (for
     * example, a missing required parameter) do <b>not</b> trigger the fallback: they are propagated to the
     * caller.
     *
     * The class is obtained like the error handler (single bean of that type or instance created with
     * dependency injection). Do not declare it as a bean that implements the client interface, or injection of
     * that interface would become ambiguous. {@code void.class} (the default value) means
     * there is no fallback.
     *
     * @return the fallback class, or {@code void.class} if there is none
     */
    Class<?> fallback() default void.class;
}
