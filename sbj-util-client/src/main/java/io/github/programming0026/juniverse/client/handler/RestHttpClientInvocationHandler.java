
package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.MethodReadTimeout;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.exception.ErrorHandler;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Serves each call to the proxy of a {@link RestHttpClient} client.
 * When created it validates the base URL and analyzes all the interface methods ({@link MethodMetadata}); a
 * badly declared method makes the creation of the client fail. On each call it only binds the arguments,
 * executes the request with RestClient or RestTemplate and wraps it with the Resilience4j decorators in the
 * order recommended by Resilience4j: Retry (the outermost) → CircuitBreaker → RateLimiter → TimeLimiter →
 * request. This way each attempt goes through the circuit breaker, consumes a rate limiter permit and has its
 * own time limit.
 *
 * @author Java Universe
 */
public class RestHttpClientInvocationHandler implements InvocationHandler {

    /**
     * Executor of the requests with a time limiter: one virtual thread per request, so as not to block the
     * common {@code ForkJoinPool} and to be able to interrupt the request when the time expires.
     */
    private static final ExecutorService TIME_LIMITER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientInvocationHandler.class);

    /** The HTTP client: a RestClient or a RestTemplate. */
    private final Object httpClient;

    /** The client configuration. */
    private final RestHttpClient clientConfig;

    /** The error handler. */
    private final ErrorHandler errorHandler;

    /** The client interface. */
    private final Class<?> clientInterface;

    /** The metadata of each HTTP method of the interface, computed only once. */
    private final Map<Method, MethodMetadata> methods;

    /** The circuit breaker, or null if disabled. */
    private final CircuitBreaker circuitBreaker;

    /** The retry for idempotent requests (always present). */
    private final Retry retry;

    /** The retry for non-idempotent requests (POST, PATCH), more restrictive (always present). */
    private final Retry nonIdempotentRetry;

    /** The rate limiter, or null if disabled. */
    private final RateLimiter rateLimiter;

    /** The time limiter, or null if disabled. */
    private final TimeLimiter timeLimiter;

    /** The implementation that answers when the service is unavailable, or null if there is none. */
    private final Object fallback;

    /** The retries and time limiter specific to the methods with {@code @MethodConfig}. */
    private final Map<MethodMetadata, MethodResilience> methodResilience;

    /**
     * Creates the handler without placeholder resolution in the mappings.
     *
     * @param httpClient the HTTP client (RestClient or RestTemplate)
     * @param clientConfig the client configuration
     * @param errorHandler the error handler
     * @param clientInterface the client interface
     * @param resilience4jConfig the factory of Resilience4j instances
     * @throws ClientException if the URL or any client method is badly declared
     */
    public RestHttpClientInvocationHandler(Object httpClient,
                                           RestHttpClient clientConfig,
                                           ErrorHandler errorHandler,
                                           Class<?> clientInterface,
                                           CircuitBreakerUtil resilience4jConfig) {
        this(httpClient, clientConfig, errorHandler, clientInterface, resilience4jConfig, UnaryOperator.identity());
    }

    /**
     * Creates the handler: validates the base URL, analyzes the interface methods and creates the client's
     * Resilience4j instances. The instances are named with the annotation's {@code name()} or, if
     * empty, with the simple name of the interface.
     *
     * @param httpClient the HTTP client (RestClient or RestTemplate)
     * @param clientConfig the client configuration (with its placeholders already resolved)
     * @param errorHandler the error handler
     * @param clientInterface the client interface
     * @param resilience4jConfig the factory of Resilience4j instances
     * @param valueResolver resolves placeholders in the mapping and parameter attributes
     * @throws ClientException if the URL or any client method is badly declared
     */
    public RestHttpClientInvocationHandler(Object httpClient,
                                           RestHttpClient clientConfig,
                                           ErrorHandler errorHandler,
                                           Class<?> clientInterface,
                                           CircuitBreakerUtil resilience4jConfig,
                                           UnaryOperator<String> valueResolver) {
        this(httpClient, clientConfig, errorHandler, clientInterface, resilience4jConfig, valueResolver, null);
    }

    /**
     * Creates the handler with a fallback. See {@link #RestHttpClientInvocationHandler(Object, RestHttpClient,
     * ErrorHandler, Class, CircuitBreakerUtil, UnaryOperator)}.
     *
     * @param httpClient the HTTP client (RestClient or RestTemplate)
     * @param clientConfig the client configuration (with its placeholders already resolved)
     * @param errorHandler the error handler
     * @param clientInterface the client interface
     * @param resilience4jConfig the factory of Resilience4j instances
     * @param valueResolver resolves placeholders in the mapping and parameter attributes
     * @param fallback the implementation of the interface that answers when the service is unavailable, or
     *                 null if there is none
     * @throws ClientException if the URL or any client method is badly declared, or if the fallback does not
     *                         implement the interface
     */
    public RestHttpClientInvocationHandler(Object httpClient,
                                           RestHttpClient clientConfig,
                                           ErrorHandler errorHandler,
                                           Class<?> clientInterface,
                                           CircuitBreakerUtil resilience4jConfig,
                                           UnaryOperator<String> valueResolver,
                                           Object fallback) {
        if (fallback != null && !clientInterface.isInstance(fallback)) {
            throw new ClientException("Fallback " + fallback.getClass().getName() + " of client "
                    + clientInterface.getSimpleName() + " must implement " + clientInterface.getName());
        }
        this.fallback = fallback;
        this.httpClient = httpClient;
        this.clientConfig = clientConfig;
        this.errorHandler = errorHandler;
        this.clientInterface = clientInterface;

        validateBaseUrl(clientConfig.url(), clientInterface);
        this.methods = analyzeMethods(clientInterface, valueResolver);
        rejectStreamingWithRestTemplate(httpClient, methods);

        String clientName = clientConfig.name().isEmpty()
                ? clientInterface.getSimpleName()
                : clientConfig.name();

        this.retry = resilience4jConfig.createRetry(clientName, clientConfig, errorHandler, true);
        this.nonIdempotentRetry = resilience4jConfig.createRetry(clientName, clientConfig, errorHandler, false);

        this.circuitBreaker = clientConfig.circuitBreaker().enabled()
                ? resilience4jConfig.createCircuitBreaker(clientName, clientConfig, errorHandler)
                : null;

        this.rateLimiter = clientConfig.rateLimiter().enabled()
                ? resilience4jConfig.createRateLimiter(clientName, clientConfig)
                : null;

        this.timeLimiter = clientConfig.timeLimiter().enabled()
                ? resilience4jConfig.createTimeLimiter(clientName, clientConfig)
                : null;

        Map<MethodMetadata, MethodResilience> overrides = new HashMap<>();
        methods.forEach((method, metadata) -> {
            MethodMetadata.MethodSettings settings = metadata.settings();
            if (settings.overridesRetries() || settings.overridesTimeLimiter()) {
                overrides.put(metadata, createMethodResilience(clientName + "#" + signature(method),
                        settings, clientConfig, errorHandler, resilience4jConfig));
            }
        });
        this.methodResilience = Map.copyOf(overrides);
    }

    /**
     * The retries and time limiter specific to a method with {@code @MethodConfig}; a null value means
     * that this aspect is inherited from the client (or, for the time limiter, that the method has no limit).
     *
     * @param retry the retry for idempotent requests, or null if it inherits the client's one
     * @param nonIdempotentRetry the retry for non-idempotent requests, or null if it inherits the client's one
     * @param timeLimiter the time limiter, or null if the method has no time limit
     */
    private record MethodResilience(Retry retry, Retry nonIdempotentRetry, TimeLimiter timeLimiter) {
    }

    /**
     * Creates the Resilience4j instances specific to a method. They carry the client name plus the method
     * signature, so as not to share state with the client's ones or with other methods.
     *
     * @param name the base name of the instances
     * @param settings the method settings
     * @param clientConfig the client configuration
     * @param errorHandler the error handler
     * @param resilience4jConfig the factory of Resilience4j instances
     * @return the method's own instances
     */
    private static MethodResilience createMethodResilience(String name, MethodMetadata.MethodSettings settings,
                                                           RestHttpClient clientConfig, ErrorHandler errorHandler,
                                                           CircuitBreakerUtil resilience4jConfig) {
        Retry methodRetry = null;
        Retry methodNonIdempotentRetry = null;
        if (settings.overridesRetries()) {
            methodRetry = resilience4jConfig.createRetry(name, clientConfig, errorHandler, true,
                    settings.maxRetries());
            methodNonIdempotentRetry = resilience4jConfig.createRetry(name, clientConfig, errorHandler, false,
                    settings.maxRetries());
        }
        TimeLimiter methodTimeLimiter = settings.overridesTimeLimiter() && settings.timeLimiterTimeout() > 0
                ? resilience4jConfig.createTimeLimiter(name, settings.timeLimiterTimeout())
                : null;
        return new MethodResilience(methodRetry, methodNonIdempotentRetry, methodTimeLimiter);
    }

    /**
     * Describes a method with its parameter types, for example {@code find(String,int)}, to name its
     * Resilience4j instances without overloads clashing.
     *
     * @param method the method
     * @return the signature
     */
    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    /**
     * Validates that the base URL is absolute with an http or https scheme and has a host.
     *
     * @param url the base URL
     * @param clientInterface the client interface, for the error message
     * @throws ClientException if the URL is not valid
     */
    private static void validateBaseUrl(String url, Class<?> clientInterface) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw invalidUrl(url, clientInterface, e);
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw invalidUrl(url, clientInterface, null);
        }
    }

    /**
     * Creates the invalid base URL exception.
     *
     * @param url the URL
     * @param clientInterface the client interface
     * @param cause the cause, or null
     * @return the exception
     */
    private static ClientException invalidUrl(String url, Class<?> clientInterface, Exception cause) {
        return new ClientException("Client " + clientInterface.getSimpleName()
                + " must declare an absolute http(s) url, but was '" + url + "'", cause);
    }

    /**
     * Analyzes all the HTTP methods of the interface (inherited ones included; {@code default} and
     * {@code static} ones are excluded).
     *
     * @param clientInterface the client interface
     * @param valueResolver resolves placeholders
     * @return the metadata per method
     * @throws ClientException if any method is badly declared
     */
    private static Map<Method, MethodMetadata> analyzeMethods(Class<?> clientInterface,
                                                              UnaryOperator<String> valueResolver) {
        RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(clientInterface, RequestMapping.class);
        Map<Method, MethodMetadata> result = new HashMap<>();
        for (Method method : clientInterface.getMethods()) {
            if (method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            result.put(method, MethodMetadata.of(method, typeMapping, valueResolver));
        }
        return Map.copyOf(result);
    }

    /**
     * An {@code InputStream} return type needs to leave the response open, and {@code RestTemplate} closes it
     * before returning: only {@code RestClient} can stream.
     *
     * @param httpClient the HTTP client
     * @param methods the methods' metadata
     * @throws ClientException if the client is a RestTemplate and any method returns an InputStream
     */
    private static void rejectStreamingWithRestTemplate(Object httpClient, Map<Method, MethodMetadata> methods) {
        if (!(httpClient instanceof RestTemplate)) {
            return;
        }
        methods.forEach((method, metadata) -> {
            if (metadata.isStreaming()) {
                throw new ClientException("Method " + MethodMetadata.describe(method) + " returns InputStream, "
                        + "which needs type = ClientType.REST_CLIENT: RestTemplate closes the response before the "
                        + "method returns, so the body cannot be streamed");
            }
        });
    }

    /**
     * Serves a call to the proxy. {@code equals}, {@code hashCode} and {@code toString} are resolved
     * on the proxy; the interface's {@code default} methods are executed as is; the rest is
     * converted into an HTTP request.
     *
     * @param proxy the proxy
     * @param method the invoked method
     * @param args the arguments
     * @return the result according to the method's return type
     * @throws Throwable the request's exception
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        MethodMetadata metadata = methods.get(method);
        if (metadata == null) {
            throw new ClientException("Method " + MethodMetadata.describe(method) + " is not part of client "
                    + clientInterface.getSimpleName());
        }

        MethodMetadata.Request request = metadata.bind(args);
        URI uri = metadata.uri(clientConfig.url(), request);
        try {
            return executeWithResilience(metadata, request, uri);
        } catch (Exception e) {
            if (fallback == null || !isUnavailability(e)) {
                throw e;
            }
            LOG.warn("{} is unavailable ({}), using fallback {}", MethodMetadata.describe(method), e,
                    fallback.getClass().getSimpleName());
            try {
                return invokeFallback(method, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    /**
     * Indicates whether the error means the service is unavailable, which is when the fallback is used: a
     * 5xx response, a network error, a timeout, an open circuit or the request limit. 4xx responses and
     * other errors (body conversion, misused client) do not.
     *
     * @param error the call's error
     * @return true if the fallback should be used
     */
    static boolean isUnavailability(Throwable error) {
        return switch (error) {
            case HttpStatusCodeException http -> http.getStatusCode().is5xxServerError();
            case ResourceAccessException ignored -> true;
            case CallNotPermittedException ignored -> true;
            case RequestNotPermitted ignored -> true;
            default -> CircuitBreakerUtil.isTimeout(error);
        };
    }

    /**
     * Executes the equivalent method of the fallback. The method of the fallback class is called and not that of
     * the interface, because the interface may not be public and then reflection could not invoke it.
     *
     * @param method the interface method
     * @param args the call's arguments
     * @return what the fallback returns
     * @throws InvocationTargetException if the fallback throws an exception (it comes in its cause)
     * @throws ClientException if the fallback class is not accessible
     */
    private Object invokeFallback(Method method, Object[] args) throws InvocationTargetException {
        try {
            return fallback.getClass().getMethod(method.getName(), method.getParameterTypes())
                    .invoke(fallback, args);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ClientException("Fallback " + fallback.getClass().getName() + " cannot be called for "
                    + MethodMetadata.describe(method) + ": declare it as a public class", e);
        }
    }

    /**
     * Implements the {@link Object} methods with proxy identity: a proxy is only equal to itself.
     *
     * @param proxy the proxy
     * @param method the Object method
     * @param args the arguments
     * @return the result
     * @throws InvocationTargetException if the invocation fails
     */
    private Object invokeObjectMethod(Object proxy, Method method, Object[] args)
            throws InvocationTargetException, IllegalAccessException {
		return switch (method.getName()) {
		    case "equals" -> proxy == args[0];
		    case "hashCode" -> System.identityHashCode(proxy);
		    case "toString" ->
				    "RestHttpClient proxy for " + clientInterface.getName()
                            + " [" + clientConfig.url() + "]";
		    default -> method.invoke(this, args);
	    };
    }

    /**
     * Executes the request wrapped with the enabled Resilience4j decorators.
     *
     * @param metadata the method metadata
     * @param request the call data
     * @param uri the request URI
     * @return the result according to the method's return type
     * @throws Exception the request's exception
     */
    private Object executeWithResilience(MethodMetadata metadata, MethodMetadata.Request request, URI uri)
            throws Exception {
        Callable<Object> call = () -> executeRequest(metadata, request, uri);

        MethodResilience own = methodResilience.get(metadata);
        boolean idempotent = isIdempotent(metadata.httpMethod());
        TimeLimiter limiter = metadata.settings().overridesTimeLimiter() ? own.timeLimiter() : timeLimiter;
        Retry selectedRetry = retryFor(own, idempotent);

        if (limiter != null) {
            call = withTimeLimit(call, limiter);
        }
        if (rateLimiter != null) {
            call = RateLimiter.decorateCallable(rateLimiter, call);
        }
        if (circuitBreaker != null) {
            call = CircuitBreaker.decorateCallable(circuitBreaker, call);
        }
        call = Retry.decorateCallable(selectedRetry, call);

        return call.call();
    }

    /**
     * Chooses the call's retry: the method's own if its {@code @MethodConfig} changes the retries and, if
     * not, the client's one. In both cases it distinguishes idempotent requests from those that are not.
     *
     * @param own the method's own settings, or null if it has none
     * @param idempotent whether the HTTP method is idempotent
     * @return the retry to apply
     */
    private Retry retryFor(MethodResilience own, boolean idempotent) {
        if (own != null && own.retry() != null) {
            return idempotent ? own.retry() : own.nonIdempotentRetry();
        }
        return idempotent ? retry : nonIdempotentRetry;
    }

    /**
     * Indicates whether the HTTP method is idempotent according to RFC 9110 (repeating it does not change the
     * result).
     *
     * @param httpMethod the HTTP method
     * @return true for GET, HEAD, OPTIONS, TRACE, PUT and DELETE
     */
    static boolean isIdempotent(HttpMethod httpMethod) {
        return HttpMethod.GET.equals(httpMethod)
                || HttpMethod.HEAD.equals(httpMethod)
                || HttpMethod.OPTIONS.equals(httpMethod)
                || HttpMethod.TRACE.equals(httpMethod)
                || HttpMethod.PUT.equals(httpMethod)
                || HttpMethod.DELETE.equals(httpMethod);
    }

    /**
     * Applies the time limiter to an attempt. The request runs in a virtual thread that is interrupted when
     * the time expires; the (checked) {@link TimeoutException} is wrapped in {@link ClientException} so the
     * proxy does not turn it into an {@code UndeclaredThrowableException}.
     *
     * @param attempt the attempt to limit
     * @param limiter the time limiter that is applied (the client's or the method's)
     * @return the attempt with a time limit
     */
    private Callable<Object> withTimeLimit(Callable<Object> attempt, TimeLimiter limiter) {
        Callable<Object> limited = TimeLimiter.decorateFutureSupplier(limiter,
                () -> TIME_LIMITER_EXECUTOR.submit(attempt));
        return () -> {
            try {
                return limited.call();
            } catch (TimeoutException e) {
                throw new ClientException("Request timed out after "
                        + limiter.getTimeLimiterConfig().getTimeoutDuration().toMillis() + " ms", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClientException("Request interrupted", e);
            }
        };
    }

    /**
     * Executes the request with the configured HTTP client. On an HTTP error it always consults the
     * {@link ErrorHandler}; if it does not throw its own exception, the original one is propagated.
     *
     * @param metadata the method metadata
     * @param request the call data
     * @param uri the request URI
     * @return the result according to the method's return type
     */
    private Object executeRequest(MethodMetadata metadata, MethodMetadata.Request request, URI uri) {
        long readTimeout = metadata.settings().readTimeout();
        if (readTimeout != -1) {
            MethodReadTimeout.set(readTimeout);
        }
        try {
            return switch (httpClient) {
                case RestClient restClient ->
                        metadata.toReturnValue(
                                executeWithRestClient(restClient, metadata, request, uri));
                case RestTemplate restTemplate ->
                        metadata.toReturnValue(
                                executeWithRestTemplate(restTemplate, metadata, request, uri));
                case null ->
                        throw new ClientException("HTTP client is null");
                default ->
                        throw new ClientException(
                                "Unsupported HTTP client type: " + httpClient.getClass().getName());
            };
        } catch (HttpStatusCodeException e) {
            errorHandler.handleError(e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } finally {
            if (readTimeout != -1) {
                MethodReadTimeout.clear();
            }
        }
    }

    /**
     * Executes the request with RestClient.
     *
     * @param client the RestClient
     * @param metadata the method metadata
     * @param request the call data
     * @param uri the request URI
     * @return the response
     */
    private static ResponseEntity<?> executeWithRestClient(RestClient client, MethodMetadata metadata,
                                                           MethodMetadata.Request request, URI uri) {
        RestClient.RequestBodySpec bodySpec = client.method(metadata.httpMethod()).uri(uri);

        bodySpec.headers(headers -> headers.addAll(request.headers()));

        if (request.body() != null) {
            bodySpec.body(request.body());
        }

        if (metadata.isStreaming()) {
            return streamResponse(bodySpec);
        }
        RestClient.ResponseSpec responseSpec = bodySpec.retrieve();
        return metadata.isBodiless()
                ? responseSpec.toBodilessEntity()
                : responseSpec.toEntity(metadata.bodyTypeReference());
    }

    /**
     * Executes the request and returns the open response, with the body as an {@link InputStream} that is read
     * progressively. The response (and its pool connection) is released when the stream is closed.
     * Retries, circuit breaker, rate limiter and time limit act until the response headers arrive; reading the
     * body is left to whoever receives the stream (each read has the client's {@code readTimeout}). A 4xx/5xx
     * is thrown as an exception, just as without streaming, and the response is closed before throwing it.
     *
     * @param bodySpec the request, already with headers and body
     * @return the response with the body open
     */
    private static ResponseEntity<InputStream> streamResponse(RestClient.RequestBodySpec bodySpec) {
        return bodySpec.exchange((clientRequest, response) -> {
            try {
                if (response.getStatusCode().isError()) {
                    throw response.createException();
                }
                return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                        .body(new ResponseInputStream(response));
            } catch (IOException | RuntimeException e) {
                response.close();
                throw e;
            }
        }, false);
    }

    /**
     * Executes the request with RestTemplate.
     *
     * @param template the RestTemplate
     * @param metadata the method metadata
     * @param request the call data
     * @param uri the request URI
     * @return the response
     */
    private static ResponseEntity<?> executeWithRestTemplate(RestTemplate template, MethodMetadata metadata,
                                                             MethodMetadata.Request request, URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(request.headers());
        HttpEntity<?> entity = new HttpEntity<>(request.body(), headers);

        return metadata.isBodiless()
                ? template.exchange(uri, metadata.httpMethod(), entity, Void.class)
                : template.exchange(uri, metadata.httpMethod(), entity, metadata.bodyTypeReference());
    }
}
