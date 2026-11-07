package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.Delete;
import com.juniverse.client.autoconfigure.Get;
import com.juniverse.client.autoconfigure.Patch;
import com.juniverse.client.autoconfigure.Post;
import com.juniverse.client.autoconfigure.Put;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.PathVariable;
import com.juniverse.client.autoconfigure.RequestHeader;
import com.juniverse.client.autoconfigure.RequestBody;
import com.juniverse.client.autoconfigure.RequestParam;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.ErrorHandler;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
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

    /** The mappingHandlerMap. */
    private final Map<Class<? extends Annotation>, MappingHandler> mappingHandlerMap =
            Map.of(
                    Get.class, new MappingHandler(HttpMethod.GET, (metadata, method) -> {
                        Get m = method.getAnnotation(Get.class);
                        metadata.path = m.value();
                        metadata.headers = extractHeaders(m.headers());
                    }),
                    Post.class, new MappingHandler(HttpMethod.POST, (metadata, method) -> {
                        Post m = method.getAnnotation(Post.class);
                        metadata.path = m.value();
                        metadata.headers = extractHeaders(m.headers());
                    }),
                    Put.class, new MappingHandler(HttpMethod.PUT, (metadata, method) -> {
                        Put m = method.getAnnotation(Put.class);
                        metadata.path = m.value();
                        metadata.headers = extractHeaders(m.headers());
                    }),
                    Delete.class, new MappingHandler(HttpMethod.DELETE, (metadata, method) -> {
                        Delete m = method.getAnnotation(Delete.class);
                        metadata.path = m.value();
                        metadata.headers = extractHeaders(m.headers());
                    }),
                    Patch.class, new MappingHandler(HttpMethod.PATCH, (metadata, method) -> {
                        Patch m = method.getAnnotation(Patch.class);
                        metadata.path = m.value();
                        metadata.headers = extractHeaders(m.headers());
                    })
            );

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

        Supplier<Object> decoratedSupplier = () -> {
            try {
                return executeRequest(metadata, method);
            } catch (Exception e) {
                throw new ClientException(e);
            }
        };

        
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
            return timeLimitedCallable.call();
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

        String url = buildUrl(metadata);
        RestClient.RequestBodySpec bodySpec = request.uri(url);

        metadata.headers.forEach(bodySpec::header);

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

        String url = buildUrl(metadata);

        HttpHeaders headers = new HttpHeaders();
        metadata.headers.forEach(headers::add);

        org.springframework.http.HttpEntity<?> entity =
                new org.springframework.http.HttpEntity<>(metadata.body, headers);

        ResponseEntity<?> response = template.exchange(
                url,
                convertToSpringHttpMethod(metadata.httpMethod),
                entity,
                method.getReturnType()
        );

        return response.getBody();
    }



    /**
     * Builds the url with proper URL encoding.
     *
     * @param metadata the metadata
     * @return the encoded URL string
     */
    private String buildUrl(RequestMetadata metadata) {
        String url = clientConfig.url() + metadata.path;

        for (Map.Entry<String, Object> entry : metadata.pathVariables.entrySet()) {
            String encodedValue = urlEncode(String.valueOf(entry.getValue()));
            url = url.replace("{" + entry.getKey() + "}", encodedValue);
        }

        if (!metadata.queryParams.isEmpty()) {
            StringBuilder queryString = new StringBuilder("?");
            metadata.queryParams.forEach((key, value) -> {
                String encodedKey = urlEncode(key);
                String encodedValue = urlEncode(String.valueOf(value));
                queryString.append(encodedKey).append("=").append(encodedValue).append("&");
            });
            url += queryString.substring(0, queryString.length() - 1);
        }

        return url;
    }

    /**
     * URL encodes a string value using UTF-8.
     *
     * @param value the value to encode
     * @return the URL-encoded string
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.warn("Failed to URL encode value: {}", value, e);
            return value;
        }
    }

    record MappingHandler(
            HttpMethod httpMethod,
            BiConsumer<RequestMetadata, Method> extractor
    ) {}

    /**
     * Extract metadata.
     *
     * @param method the method
     * @param args the args
     * @return the request metadata
     */
    private RequestMetadata extractMetadata(Method method, Object[] args) {
        RequestMetadata metadata = new RequestMetadata();

        for (var entry : mappingHandlerMap.entrySet()) {
            if (method.isAnnotationPresent(entry.getKey())) {
                MappingHandler handler = entry.getValue();
                metadata.httpMethod = handler.httpMethod();
                handler.extractor().accept(metadata, method);
                break;
            }
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Object arg = args[i];

            if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = param.getAnnotation(PathVariable.class);
                String name = pv.value().isEmpty() ? param.getName() : pv.value();
                metadata.pathVariables.put(name, arg);
            } else if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = param.getAnnotation(RequestParam.class);
                String name = rp.value().isEmpty() ? param.getName() : rp.value();
                metadata.queryParams.put(name, arg);
            } else if (param.isAnnotationPresent(RequestBody.class)) {
                metadata.body = arg;
            } else if (param.isAnnotationPresent(RequestHeader.class)) {
                RequestHeader rh = param.getAnnotation(RequestHeader.class);
                metadata.headers.put(rh.value(), String.valueOf(arg));
            }
        }

        return metadata;
    }

    /**
     * Extract headers.
     *
     * @param headerArray the header array
     * @return the map
     */
    private Map<String, String> extractHeaders(String[] headerArray) {
        Map<String, String> headers = new HashMap<>();
        for (String header : headerArray) {
            String[] parts = header.split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            } else {
                LOG.warn("Invalid header format (missing colon): {}", header);
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
        
        /** The path variables. */
        private final Map<String, Object> pathVariables = new HashMap<>();
        
        /** The query params. */
        private final Map<String, Object> queryParams = new HashMap<>();
        
        /** The headers. */
        private Map<String, String> headers = new HashMap<>();
        
        /** The body. */
        private Object body;
    }
}
