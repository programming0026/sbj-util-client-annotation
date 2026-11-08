package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.MethodReadTimeout;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.ErrorHandler;

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

    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientInvocationHandler.class);

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

    /** La implementación que responde cuando el servicio no está disponible, o null si no hay. */
    private final Object fallback;

    /** Los reintentos y el time limiter propios de los métodos con {@code @MethodConfig}. */
    private final Map<MethodMetadata, MethodResilience> methodResilience;

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
        this(httpClient, clientConfig, errorHandler, clientInterface, resilience4jConfig, valueResolver, null);
    }

    /**
     * Crea el handler con un fallback. Ver {@link #RestHttpClientInvocationHandler(Object, RestHttpClient,
     * ErrorHandler, Class, CircuitBreakerUtil, UnaryOperator)}.
     *
     * @param httpClient el cliente HTTP (RestClient o RestTemplate)
     * @param clientConfig la configuración del cliente (con sus placeholders ya resueltos)
     * @param errorHandler el manejador de errores
     * @param clientInterface la interfaz del cliente
     * @param resilience4jConfig la fábrica de instancias de Resilience4j
     * @param valueResolver resuelve placeholders en los atributos de mapping y de parámetros
     * @param fallback la implementación de la interfaz que responde cuando el servicio no está disponible, o
     *                 null si no hay
     * @throws ClientException si la URL o algún método del cliente están mal declarados, o si el fallback no
     *                         implementa la interfaz
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
     * Los reintentos y el time limiter propios de un método con {@code @MethodConfig}; un valor null significa
     * que ese aspecto se hereda del cliente (o, en el time limiter, que el método no tiene límite).
     *
     * @param retry el retry de las peticiones idempotentes, o null si hereda el del cliente
     * @param nonIdempotentRetry el retry de las no idempotentes, o null si hereda el del cliente
     * @param timeLimiter el time limiter, o null si el método no tiene límite de tiempo
     */
    private record MethodResilience(Retry retry, Retry nonIdempotentRetry, TimeLimiter timeLimiter) {
    }

    /**
     * Crea las instancias de Resilience4j propias de un método. Llevan el nombre del cliente más la firma del
     * método, para no compartir estado con las del cliente ni con otros métodos.
     *
     * @param name el nombre base de las instancias
     * @param settings los ajustes del método
     * @param clientConfig la configuración del cliente
     * @param errorHandler el manejador de errores
     * @param resilience4jConfig la fábrica de instancias de Resilience4j
     * @return las instancias propias del método
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
     * Describe un método con sus tipos de parámetros, por ejemplo {@code find(String,int)}, para nombrar sus
     * instancias de Resilience4j sin que choquen las sobrecargas.
     *
     * @param method el método
     * @return la firma
     */
    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
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
     * Un retorno {@code InputStream} necesita dejar la respuesta abierta, y {@code RestTemplate} la cierra antes
     * de devolver: solo {@code RestClient} puede hacer streaming.
     *
     * @param httpClient el cliente HTTP
     * @param methods los metadatos de los métodos
     * @throws ClientException si el cliente es un RestTemplate y algún método devuelve un InputStream
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
        try {
            return executeWithResilience(metadata, request, uri);
        } catch (Exception e) {
            if (fallback == null || !isUnavailability(e)) {
                throw e;
            }
            LOG.warn("{} is unavailable ({}), using fallback {}", MethodMetadata.describe(method), e.toString(),
                    fallback.getClass().getSimpleName());
            return invokeFallback(method, args);
        }
    }

    /**
     * Indica si el error significa que el servicio no está disponible, que es cuando se usa el fallback: una
     * respuesta 5xx, un error de red, un timeout, el circuito abierto o el límite de peticiones. Los 4xx y
     * los demás errores (conversión del cuerpo, cliente mal usado) no.
     *
     * @param error el error de la llamada
     * @return true si corresponde usar el fallback
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
     * Ejecuta el método equivalente del fallback.
     *
     * @param method el método de la interfaz
     * @param args los argumentos de la llamada
     * @return lo que devuelve el fallback
     * @throws Throwable lo que lance el fallback
     */
    private Object invokeFallback(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(fallback, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Implementa los métodos de {@link Object} con identidad de proxy: un proxy solo es igual a sí mismo.
     *
     * @param proxy el proxy
     * @param method el método de Object
     * @param args los argumentos
     * @return el resultado
     * @throws InvocationTargetException si falla la invocación
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

        MethodResilience own = methodResilience.get(metadata);
        boolean idempotent = isIdempotent(metadata.httpMethod());
        TimeLimiter limiter = metadata.settings().overridesTimeLimiter() ? own.timeLimiter() : timeLimiter;
        Retry methodRetry = own == null || own.retry() == null ? null
                : (idempotent ? own.retry() : own.nonIdempotentRetry());

        if (limiter != null) {
            call = withTimeLimit(call, limiter);
        }
        if (rateLimiter != null) {
            call = RateLimiter.decorateCallable(rateLimiter, call);
        }
        if (circuitBreaker != null) {
            call = CircuitBreaker.decorateCallable(circuitBreaker, call);
        }
        call = Retry.decorateCallable(methodRetry != null ? methodRetry
                : (idempotent ? retry : nonIdempotentRetry), call);

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
     * @param limiter el time limiter que se aplica (el del cliente o el del método)
     * @return el intento con límite de tiempo
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
     * Ejecuta la petición con el cliente HTTP configurado. Ante un error HTTP siempre consulta al
     * {@link ErrorHandler}; si no lanza su propia excepción, se propaga la original.
     *
     * @param metadata los metadatos del método
     * @param request los datos de la llamada
     * @param uri la URI de la petición
     * @return el resultado según el tipo de retorno del método
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

        if (metadata.isStreaming()) {
            return streamResponse(bodySpec);
        }
        RestClient.ResponseSpec responseSpec = bodySpec.retrieve();
        return metadata.isBodiless()
                ? responseSpec.toBodilessEntity()
                : responseSpec.toEntity(metadata.bodyTypeReference());
    }

    /**
     * Ejecuta la petición y devuelve la respuesta abierta, con el cuerpo como {@link InputStream} que se lee
     * progresivamente. La respuesta (y su conexión del pool) se libera al cerrar el flujo.
     *
     * Reintentos, circuit breaker, rate limiter y límite de tiempo actúan hasta que llegan los headers de la
     * respuesta; la lectura del cuerpo queda en manos de quien recibe el flujo (cada lectura tiene el
     * {@code readTimeout} del cliente). Un 4xx/5xx se lanza como excepción, igual que sin streaming, y la
     * respuesta se cierra antes de lanzarla.
     *
     * @param bodySpec la petición, ya con headers y cuerpo
     * @return la respuesta con el cuerpo abierto
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
