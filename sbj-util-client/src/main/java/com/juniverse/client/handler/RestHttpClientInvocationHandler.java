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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

/**
 * Atiende cada llamada al proxy de un cliente {@link RestHttpClient}.
 *
 * Al crearse valida la URL base y analiza todos los métodos de la interfaz ({@link MethodMetadata}); un
 * método mal declarado hace fallar la creación del cliente. En cada llamada solo enlaza los argumentos,
 * ejecuta la petición con RestClient o RestTemplate y la envuelve con los decoradores de Resilience4j en el
 * orden que recomienda Resilience4j: Retry (el más externo) → CircuitBreaker → RateLimiter → TimeLimiter →
 * petición. Así cada intento pasa por el circuit breaker, consume un permiso del rate limiter y tiene su
 * propio límite de tiempo.
 */
public class RestHttpClientInvocationHandler implements InvocationHandler {

    /**
     * Ejecutor de las peticiones con time limiter: un hilo virtual por petición, para no bloquear el
     * {@code ForkJoinPool} común y para poder interrumpir la petición cuando vence el tiempo.
     */
    private static final ExecutorService TIME_LIMITER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** El cliente HTTP: un RestClient o un RestTemplate. */
    private final Object httpClient;

    /** La configuración del cliente. */
    private final RestHttpClient clientConfig;

    /** El manejador de errores. */
    private final ErrorHandler errorHandler;

    /** La interfaz del cliente. */
    private final Class<?> clientInterface;

    /** Los metadatos de cada método HTTP de la interfaz, calculados una sola vez. */
    private final Map<Method, MethodMetadata> methods;

    /** El circuit breaker, o null si está deshabilitado. */
    private final CircuitBreaker circuitBreaker;

    /** El retry de las peticiones idempotentes (siempre presente). */
    private final Retry retry;

    /** El retry de las peticiones no idempotentes (POST, PATCH), más restrictivo (siempre presente). */
    private final Retry nonIdempotentRetry;

    /** El rate limiter, o null si está deshabilitado. */
    private final RateLimiter rateLimiter;

    /** El time limiter, o null si está deshabilitado. */
    private final TimeLimiter timeLimiter;

    /**
     * Crea el handler sin resolución de placeholders en los mappings.
     *
     * @param httpClient el cliente HTTP (RestClient o RestTemplate)
     * @param clientConfig la configuración del cliente
     * @param errorHandler el manejador de errores
     * @param clientInterface la interfaz del cliente
     * @param resilience4jConfig la fábrica de instancias de Resilience4j
     * @throws ClientException si la URL o algún método del cliente están mal declarados
     */
    public RestHttpClientInvocationHandler(Object httpClient,
                                           RestHttpClient clientConfig,
                                           ErrorHandler errorHandler,
                                           Class<?> clientInterface,
                                           CircuitBreakerUtil resilience4jConfig) {
        this(httpClient, clientConfig, errorHandler, clientInterface, resilience4jConfig, UnaryOperator.identity());
    }

    /**
     * Crea el handler: valida la URL base, analiza los métodos de la interfaz y crea las instancias de
     * Resilience4j del cliente. Las instancias se nombran con {@code name()} de la anotación o, si está
     * vacío, con el nombre simple de la interfaz.
     *
     * @param httpClient el cliente HTTP (RestClient o RestTemplate)
     * @param clientConfig la configuración del cliente (con sus placeholders ya resueltos)
     * @param errorHandler el manejador de errores
     * @param clientInterface la interfaz del cliente
     * @param resilience4jConfig la fábrica de instancias de Resilience4j
     * @param valueResolver resuelve placeholders en los atributos de mapping y de parámetros
     * @throws ClientException si la URL o algún método del cliente están mal declarados
     */
    public RestHttpClientInvocationHandler(Object httpClient,
                                           RestHttpClient clientConfig,
                                           ErrorHandler errorHandler,
                                           Class<?> clientInterface,
                                           CircuitBreakerUtil resilience4jConfig,
                                           UnaryOperator<String> valueResolver) {
        this.httpClient = httpClient;
        this.clientConfig = clientConfig;
        this.errorHandler = errorHandler;
        this.clientInterface = clientInterface;

        validateBaseUrl(clientConfig.url(), clientInterface);
        this.methods = analyzeMethods(clientInterface, valueResolver);

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
    }

    /**
     * Valida que la URL base sea absoluta con esquema http o https y con host.
     *
     * @param url la URL base
     * @param clientInterface la interfaz del cliente, para el mensaje de error
     * @throws ClientException si la URL no es válida
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
     * Crea la excepción de URL base inválida.
     *
     * @param url la URL
     * @param clientInterface la interfaz del cliente
     * @param cause la causa, o null
     * @return la excepción
     */
    private static ClientException invalidUrl(String url, Class<?> clientInterface, Exception cause) {
        return new ClientException("Client " + clientInterface.getSimpleName()
                + " must declare an absolute http(s) url, but was '" + url + "'", cause);
    }

    /**
     * Analiza todos los métodos HTTP de la interfaz (los heredados incluidos; se excluyen los
     * {@code default} y los {@code static}).
     *
     * @param clientInterface la interfaz del cliente
     * @param valueResolver resuelve placeholders
     * @return los metadatos por método
     * @throws ClientException si algún método está mal declarado
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
     * Atiende una llamada al proxy. {@code equals}, {@code hashCode} y {@code toString} se resuelven
     * sobre el proxy; los métodos {@code default} de la interfaz se ejecutan tal cual; el resto se
     * convierte en una petición HTTP.
     *
     * @param proxy el proxy
     * @param method el método invocado
     * @param args los argumentos
     * @return el resultado según el tipo de retorno del método
     * @throws Throwable la excepción de la petición
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
        return executeWithResilience(metadata, request, uri);
    }

    /**
     * Implementa los métodos de {@link Object} con identidad de proxy: un proxy solo es igual a sí mismo.
     *
     * @param proxy el proxy
     * @param method el método de Object
     * @param args los argumentos
     * @return el resultado
     * @throws Exception si falla la invocación
     */
    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) throws Exception {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "RestHttpClient proxy for " + clientInterface.getName() + " [" + clientConfig.url() + "]";
            default:
                return method.invoke(this, args);
        }
    }

    /**
     * Ejecuta la petición envuelta con los decoradores de Resilience4j habilitados.
     *
     * @param metadata los metadatos del método
     * @param request los datos de la llamada
     * @param uri la URI de la petición
     * @return el resultado según el tipo de retorno del método
     * @throws Exception la excepción de la petición
     */
    private Object executeWithResilience(MethodMetadata metadata, MethodMetadata.Request request, URI uri)
            throws Exception {
        Callable<Object> call = () -> executeRequest(metadata, request, uri);

        if (timeLimiter != null) {
            call = withTimeLimit(call);
        }
        if (rateLimiter != null) {
            call = RateLimiter.decorateCallable(rateLimiter, call);
        }
        if (circuitBreaker != null) {
            call = CircuitBreaker.decorateCallable(circuitBreaker, call);
        }
        call = Retry.decorateCallable(isIdempotent(metadata.httpMethod()) ? retry : nonIdempotentRetry, call);

        return call.call();
    }

    /**
     * Indica si el método HTTP es idempotente según RFC 9110 (repetirlo no cambia el resultado).
     *
     * @param httpMethod el método HTTP
     * @return true para GET, HEAD, OPTIONS, TRACE, PUT y DELETE
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
     * Aplica el time limiter a un intento. La petición corre en un hilo virtual que se interrumpe al vencer
     * el tiempo; el {@link TimeoutException} (checked) se envuelve en {@link ClientException} para que el
     * proxy no lo convierta en {@code UndeclaredThrowableException}.
     *
     * @param attempt el intento a limitar
     * @return el intento con límite de tiempo
     */
    private Callable<Object> withTimeLimit(Callable<Object> attempt) {
        Callable<Object> limited = TimeLimiter.decorateFutureSupplier(timeLimiter,
                () -> TIME_LIMITER_EXECUTOR.submit(attempt));
        return () -> {
            try {
                return limited.call();
            } catch (TimeoutException e) {
                throw new ClientException("Request timed out after "
                        + clientConfig.timeLimiter().timeout() + " ms", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClientException("Request interrupted", e);
            }
        };
    }

    /**
     * Ejecuta la petición con el cliente HTTP configurado. Ante un error HTTP siempre consulta al
     * {@link ErrorHandler}; si no lanza su propia excepción, se propaga la original.
     *
     * @param metadata los metadatos del método
     * @param request los datos de la llamada
     * @param uri la URI de la petición
     * @return el resultado según el tipo de retorno del método
     */
    private Object executeRequest(MethodMetadata metadata, MethodMetadata.Request request, URI uri) {
        try {
            if (httpClient instanceof RestClient restClient) {
                return metadata.toReturnValue(executeWithRestClient(restClient, metadata, request, uri));
            } else if (httpClient instanceof RestTemplate restTemplate) {
                return metadata.toReturnValue(executeWithRestTemplate(restTemplate, metadata, request, uri));
            } else {
                throw new ClientException("Unsupported HTTP client type: "
                        + httpClient.getClass().getName());
            }
        } catch (HttpStatusCodeException e) {
            errorHandler.handleError(e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        }
    }

    /**
     * Ejecuta la petición con RestClient.
     *
     * @param client el RestClient
     * @param metadata los metadatos del método
     * @param request los datos de la llamada
     * @param uri la URI de la petición
     * @return la respuesta
     */
    private static ResponseEntity<?> executeWithRestClient(RestClient client, MethodMetadata metadata,
                                                           MethodMetadata.Request request, URI uri) {
        RestClient.RequestBodySpec bodySpec = client.method(metadata.httpMethod()).uri(uri);

        bodySpec.headers(headers -> headers.addAll(request.headers()));

        if (request.body() != null) {
            bodySpec.body(request.body());
        }

        RestClient.ResponseSpec responseSpec = bodySpec.retrieve();
        return metadata.isBodiless()
                ? responseSpec.toBodilessEntity()
                : responseSpec.toEntity(metadata.bodyTypeReference());
    }

    /**
     * Ejecuta la petición con RestTemplate.
     *
     * @param template el RestTemplate
     * @param metadata los metadatos del método
     * @param request los datos de la llamada
     * @param uri la URI de la petición
     * @return la respuesta
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
