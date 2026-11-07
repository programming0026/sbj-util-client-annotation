package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.ErrorHandler;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class RestHttpClientInvocationHandler.
 */
public class RestHttpClientInvocationHandler implements InvocationHandler {
    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientInvocationHandler.class);

    /** Prefix of the URI template variables generated for query parameter values. */
    private static final String QUERY_PARAM_VARIABLE_PREFIX = "__queryParam";

    /** The http client. */
    private final Object httpClient;
    
    /** The client config. */
    private final RestHttpClient clientConfig;
    
    /** The error handler. */
    private final ErrorHandler errorHandler;
    
    /** The circuit breaker. */
    private final CircuitBreaker circuitBreaker;
    
    /** The retry. */
    private final Retry retry;
    
    /** The rate limiter. */
    private final RateLimiter rateLimiter;
    
    /** The time limiter. */
    private final TimeLimiter timeLimiter;

    /**
     * Instantiates a new rest http client invocation handler.
     *
     * @param httpClient the http client
     * @param clientConfig the client config
     * @param errorHandler the error handler
     * @param clientInterface the client interface
     * @param resilience4jConfig the resilience 4 j config
     */
    public RestHttpClientInvocationHandler(Object httpClient,
                                           RestHttpClient clientConfig,
                                           ErrorHandler errorHandler,
                                           Class<?> clientInterface,
                                           CircuitBreakerUtil resilience4jConfig) {
        this.httpClient = httpClient;
        this.clientConfig = clientConfig;
        this.errorHandler = errorHandler;

        String clientName = clientConfig.name().isEmpty() 
        		? clientInterface.getSimpleName() 
        		: clientConfig.name();
                

        this.retry = resilience4jConfig.createRetry(clientName, clientConfig, errorHandler);


        this.circuitBreaker = clientConfig.circuitBreaker().enabled()
                ? resilience4jConfig.createCircuitBreaker(clientName, clientConfig)
                : null;


        this.rateLimiter = clientConfig.rateLimiter().enabled()
                ? resilience4jConfig.createRateLimiter(clientName, clientConfig)
                : null;


        this.timeLimiter = clientConfig.timeLimiter().enabled()
                ? resilience4jConfig.createTimeLimiter(clientName, clientConfig)
                : null;
    }

    /**
     * Invoke.
     *
     * @param proxy the proxy
     * @param method the method
     * @param args the args
     * @return the object
     * @throws Throwable the throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        RequestMetadata metadata = extractMetadata(method, args);

        return executeWithResilience(metadata, method);
    }

    /**
     * Execute with resilience.
     *
     * @param metadata the metadata
     * @param method the method
     * @return the object
     * @Exception the throwable
     */
    private Object executeWithResilience(RequestMetadata metadata, Method method) throws Exception {

        // Exceptions are propagated unwrapped so the retry predicate (ErrorHandler.shouldRetry) and the
        // circuit breaker see the actual HttpStatusCodeException, and callers receive it as-is.
        Supplier<Object> decoratedSupplier = () -> executeRequest(metadata, method);

        
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);

       
        if (circuitBreaker != null) {
            decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, decoratedSupplier);
        }

       
        if (rateLimiter != null) {
            decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter, decoratedSupplier);
        }

        
        if (timeLimiter != null) {
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(decoratedSupplier);
            Callable<Object> timeLimitedCallable = TimeLimiter.decorateFutureSupplier(
                    timeLimiter,
                    () -> future
            );
            try {
                return timeLimitedCallable.call();
            } catch (TimeoutException e) {
                // TimeoutException is checked: wrap it so callers going through the proxy get a
                // ClientException instead of an UndeclaredThrowableException.
                throw new ClientException("Request timed out after "
                        + clientConfig.timeLimiter().timeout() + " ms", e);
            }
        }

        return decoratedSupplier.get();
    }

    /**
     * Execute request.
     *
     * @param metadata the metadata
     * @param method the method
     * @return the object
     */
    private Object executeRequest(RequestMetadata metadata, Method method)  {
        try {
            if (httpClient instanceof RestClient) {
                return executeWithRestClient(metadata, method);
            } else if (httpClient instanceof RestTemplate) {
                return executeWithRestTemplate(metadata, method);
            } else {
                throw new ClientException("Unsupported HTTP client type: "
                        + httpClient.getClass().getName());
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {

            if (circuitBreaker != null && errorHandler.shouldRecordAsFailure(e.getStatusCode())) {
                throw e;
            }
            errorHandler.handleError(e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        }
    }

    /**
     * Execute with rest client.
     *
     * @param metadata the metadata
     * @param method the method
     * @return the object
     */
    private Object executeWithRestClient(RequestMetadata metadata, Method method) {
        RestClient client = (RestClient) httpClient;

        RestClient.RequestBodyUriSpec request = client.method(metadata.httpMethod);

        RestClient.RequestBodySpec bodySpec = request.uri(buildUri(metadata));

        bodySpec.headers(headers -> headers.addAll(metadata.headers));

        if (metadata.body != null) {
            bodySpec.body(metadata.body);
        }

        ResponseEntity<?> response = bodySpec.retrieve()
                .toEntity(method.getReturnType());

        return response.getBody();
    }

    /**
     * Execute with rest template.
     *
     * @param metadata the metadata
     * @param method the method
     * @return the object
     */
    private Object executeWithRestTemplate(RequestMetadata metadata, Method method) {
        RestTemplate template = (RestTemplate) httpClient;

        URI uri = buildUri(metadata);

        HttpHeaders headers = new HttpHeaders();
        headers.addAll(metadata.headers);

        org.springframework.http.HttpEntity<?> entity =
                new org.springframework.http.HttpEntity<>(metadata.body, headers);

        ResponseEntity<?> response = template.exchange(
                uri,
                convertToSpringHttpMethod(metadata.httpMethod),
                entity,
                method.getReturnType()
        );

        return response.getBody();
    }



    /**
     * Builds the fully encoded request URI.
     *
     * Path variables and query parameter values are expanded as URI template variables, so each value
     * is encoded exactly once (reserved characters such as '/', '&amp;' or '=' included). The result is a
     * {@link URI}, which RestClient and RestTemplate send as-is without encoding it again.
     *
     * @param metadata the metadata
     * @return the encoded URI
     */
    private URI buildUri(RequestMetadata metadata) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(clientConfig.url() + metadata.path);
        Map<String, Object> uriVariables = new HashMap<>(metadata.pathVariables);

        // Each value (one per element for collections/arrays) becomes its own template variable,
        // producing repeated parameters: ?tag=a&tag=b
        int index = 0;
        for (Map.Entry<String, List<String>> entry : metadata.queryParams.entrySet()) {
            for (String value : entry.getValue()) {
                String variable = QUERY_PARAM_VARIABLE_PREFIX + index++;
                builder.queryParam(entry.getKey(), "{" + variable + "}");
                uriVariables.put(variable, value);
            }
        }

        return builder.encode().buildAndExpand(uriVariables).toUri();
    }

    /**
     * Extract metadata.
     *
     * @param method the method
     * @param args the args
     * @return the request metadata
     */
    private RequestMetadata extractMetadata(Method method, Object[] args) {
        RequestMetadata metadata = new RequestMetadata();

        // Resolves @GetMapping, @PostMapping, @PutMapping, @PatchMapping, @DeleteMapping and
        // @RequestMapping(method = ...) uniformly, including the value/path alias.
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null || mapping.method().length == 0) {
            throw new ClientException("Method " + method.getDeclaringClass().getSimpleName() + "."
                    + method.getName() + " must be annotated with @GetMapping, @PostMapping, @PutMapping, "
                    + "@PatchMapping, @DeleteMapping or @RequestMapping(method = ...)");
        }
        metadata.httpMethod = mapping.method()[0].asHttpMethod();
        metadata.path = mapping.path().length > 0 ? mapping.path()[0] : "";
        if (mapping.produces().length > 0) {
            metadata.headers.set(HttpHeaders.ACCEPT, String.join(", ", mapping.produces()));
        }
        if (mapping.consumes().length > 0) {
            metadata.headers.set(HttpHeaders.CONTENT_TYPE, mapping.consumes()[0]);
        }
        extractHeaders(mapping.headers()).forEach(metadata.headers::set);

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            bindParameter(metadata, method, parameters[i], args[i]);
        }

        return metadata;
    }

    /**
     * Binds one method argument using Spring's {@link PathVariable}, {@link RequestParam},
     * {@link RequestHeader} and {@link RequestBody} annotations. Merged annotations are used so the
     * {@code value}/{@code name} alias is resolved. Arguments without any of them are ignored.
     * Parameters declared as {@link Map} (including {@code MultiValueMap}) contribute one entry per key.
     *
     * @param metadata the request metadata
     * @param method the invoked method
     * @param param the parameter
     * @param arg the argument value
     */
    private void bindParameter(RequestMetadata metadata, Method method, Parameter param, Object arg) {
        PathVariable pathVariable = AnnotatedElementUtils.findMergedAnnotation(param, PathVariable.class);
        if (pathVariable != null) {
            if (isMap(param)) {
                bindMapEntries(arg, pathVariable.required(), "path variable map", param, method,
                        metadata::addPathVariable);
                return;
            }
            String name = nameOf(pathVariable.name(), param);
            List<String> values = toValues(arg);
            if (values.isEmpty()) {
                throw missingValue("path variable", name, method);
            }
            metadata.addPathVariable(name, values);
            return;
        }

        RequestParam requestParam = AnnotatedElementUtils.findMergedAnnotation(param, RequestParam.class);
        if (requestParam != null) {
            if (isMap(param)) {
                bindMapEntries(arg, requestParam.required(), "request parameter map", param, method,
                        metadata::addQueryParam);
                return;
            }
            String name = nameOf(requestParam.name(), param);
            List<String> values = resolveValues(arg, requestParam.defaultValue(), requestParam.required(),
                    "request parameter", name, method);
            if (!values.isEmpty()) {
                metadata.addQueryParam(name, values);
            }
            return;
        }

        RequestHeader requestHeader = AnnotatedElementUtils.findMergedAnnotation(param, RequestHeader.class);
        if (requestHeader != null) {
            if (isMap(param)) {
                bindMapEntries(arg, requestHeader.required(), "request header map", param, method,
                        metadata::setHeader);
                return;
            }
            String name = nameOf(requestHeader.name(), param);
            List<String> values = resolveValues(arg, requestHeader.defaultValue(), requestHeader.required(),
                    "request header", name, method);
            if (!values.isEmpty()) {
                metadata.setHeader(name, values);
            }
            return;
        }

        RequestBody requestBody = AnnotatedElementUtils.findMergedAnnotation(param, RequestBody.class);
        if (requestBody != null) {
            if (arg == null && requestBody.required()) {
                throw missingValue("request body", param.getName(), method);
            }
            metadata.body = arg;
        }
    }

    /**
     * Whether the parameter is declared as a {@link Map}.
     *
     * @param param the parameter
     * @return true for Map (and MultiValueMap) parameters
     */
    private static boolean isMap(Parameter param) {
        return Map.class.isAssignableFrom(param.getType());
    }

    /**
     * Binds every entry of a Map argument. Entry values go through {@link #toValues(Object)}, so a
     * {@code MultiValueMap} or collection values produce several values; entries with a {@code null} key
     * or without values are skipped. Only a {@code null} map counts as missing: an empty map sends nothing.
     *
     * @param arg the Map argument (may be null)
     * @param required whether the map is required
     * @param kind the kind of value, for the error message
     * @param param the parameter
     * @param method the invoked method
     * @param sink receives each entry name with its values
     */
    private static void bindMapEntries(Object arg, boolean required, String kind, Parameter param, Method method,
                                       BiConsumer<String, List<String>> sink) {
        if (arg == null) {
            if (required) {
                throw missingValue(kind, param.getName(), method);
            }
            return;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) arg).entrySet()) {
            List<String> values = toValues(entry.getValue());
            if (entry.getKey() != null && !values.isEmpty()) {
                sink.accept(String.valueOf(entry.getKey()), values);
            }
        }
    }

    /**
     * Returns the annotation name, or the parameter name (requires {@code -parameters}) when empty.
     *
     * @param annotatedName the name declared in the annotation
     * @param param the parameter
     * @return the effective name
     */
    private static String nameOf(String annotatedName, Parameter param) {
        return annotatedName.isEmpty() ? param.getName() : annotatedName;
    }

    /**
     * Resolves an argument into the values to send: its values (see {@link #toValues(Object)}), else the
     * annotation default value, else an empty list when optional. A {@code null} argument and an empty
     * collection/array are both treated as missing.
     *
     * @param arg the argument
     * @param defaultValue the annotation default value ({@link ValueConstants#DEFAULT_NONE} if unset)
     * @param required whether the value is required
     * @param kind the kind of value, for the error message
     * @param name the value name
     * @param method the invoked method
     * @return the values to send; empty to omit them
     */
    private static List<String> resolveValues(Object arg, String defaultValue, boolean required, String kind,
                                              String name, Method method) {
        List<String> values = toValues(arg);
        if (!values.isEmpty()) {
            return values;
        }
        if (!ValueConstants.DEFAULT_NONE.equals(defaultValue)) {
            return List.of(defaultValue);
        }
        if (required) {
            throw missingValue(kind, name, method);
        }
        return List.of();
    }

    /**
     * Converts an argument to its string values: one per element for an {@link Iterable} or an array
     * (primitive arrays included, {@code null} elements skipped), a single value otherwise, none for
     * {@code null}.
     *
     * @param arg the argument
     * @return the values
     */
    private static List<String> toValues(Object arg) {
        if (arg == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (arg instanceof Iterable<?> iterable) {
            iterable.forEach(element -> addIfNotNull(values, element));
        } else if (arg.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(arg); i++) {
                addIfNotNull(values, Array.get(arg, i));
            }
        } else {
            values.add(String.valueOf(arg));
        }
        return values;
    }

    /**
     * Adds the string form of a value when it is not {@code null}.
     *
     * @param values the target list
     * @param value the value
     */
    private static void addIfNotNull(List<String> values, Object value) {
        if (value != null) {
            values.add(String.valueOf(value));
        }
    }

    /**
     * Builds the exception for a required value that was {@code null}.
     *
     * @param kind the kind of value
     * @param name the value name
     * @param method the invoked method
     * @return the exception
     */
    private static ClientException missingValue(String kind, String name, Method method) {
        return new ClientException("Required " + kind + " '" + name + "' is null or empty in "
                + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }

    /**
     * Extract headers.
     *
     * Uses the Spring mapping format {@code "Name=value"}. Expressions without a value
     * (e.g. {@code "Name"} or {@code "!Name"}) are server-side conditions and are ignored.
     *
     * @param headerArray the header array
     * @return the map
     */
    private Map<String, String> extractHeaders(String[] headerArray) {
        Map<String, String> headers = new HashMap<>();
        for (String header : headerArray) {
            String[] parts = header.split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[0].trim().endsWith("!")) {
                headers.put(parts[0].trim(), parts[1].trim());
            } else {
                LOG.warn("Ignoring header expression without a value (expected 'Name=value'): {}", header);
            }
        }
        return headers;
    }

    /**
     * Convert to spring http method.
     *
     * @param method the method
     * @return the org.springframework.http. http method
     */
    private HttpMethod convertToSpringHttpMethod(HttpMethod method) {
        return HttpMethod.valueOf(method.name());
    }

    /**
     * The Class RequestMetadata.
     */
    private static class RequestMetadata {
        
        /** The path. */
        private String path;
        
        /** The http method. */
        private HttpMethod httpMethod;
        
        /** The path variables (multiple values already joined with commas). */
        private final Map<String, Object> pathVariables = new HashMap<>();

        /** The query params, in declaration order; one entry per value. */
        private final Map<String, List<String>> queryParams = new LinkedHashMap<>();

        /** The headers (case-insensitive, multi-valued). */
        private final HttpHeaders headers = new HttpHeaders();
        
        /** The body. */
        private Object body;

        /**
         * Adds a path variable; like Spring, multiple values are joined with commas (then encoded).
         *
         * @param name the variable name
         * @param values the values
         */
        private void addPathVariable(String name, List<String> values) {
            pathVariables.put(name, String.join(",", values));
        }

        /**
         * Appends query parameter values.
         *
         * @param name the parameter name
         * @param values the values
         */
        private void addQueryParam(String name, List<String> values) {
            queryParams.computeIfAbsent(name, key -> new ArrayList<>()).addAll(values);
        }

        /**
         * Sets a header, replacing any value declared in the mapping's headers attribute.
         *
         * @param name the header name
         * @param values the values
         */
        private void setHeader(String name, List<String> values) {
            headers.remove(name);
            headers.addAll(name, values);
        }
    }
}
