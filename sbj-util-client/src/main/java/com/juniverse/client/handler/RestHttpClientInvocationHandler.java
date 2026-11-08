package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.ErrorHandler;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atiende cada llamada al proxy de un cliente {@link RestHttpClient}.
 *
 * Lee las anotaciones de Spring Web de la interfaz, del método y de sus parámetros, arma la petición, la
 * ejecuta con RestClient o RestTemplate y la envuelve con los decoradores de Resilience4j en el orden que
 * recomienda Resilience4j: Retry (el más externo) → CircuitBreaker → RateLimiter → TimeLimiter → petición.
 * Así cada intento pasa por el circuit breaker, consume un permiso del rate limiter y tiene su propio
 * límite de tiempo.
 */
public class RestHttpClientInvocationHandler implements InvocationHandler {
    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientInvocationHandler.class);

    /** Prefijo de las variables de plantilla URI que se generan para los valores de query params. */
    private static final String QUERY_PARAM_VARIABLE_PREFIX = "__queryParam";

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

    /** El {@code @RequestMapping} declarado en la interfaz, o null si no tiene. */
    private final RequestMapping typeMapping;

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

    /** Resuelve placeholders {@code ${...}} en los atributos de mapping y de parámetros. */
    private final UnaryOperator<String> valueResolver;

    /**
     * Crea el handler sin resolución de placeholders en los mappings.
     *
     * @param httpClient el cliente HTTP (RestClient o RestTemplate)
     * @param clientConfig la configuración del cliente
     * @param errorHandler el manejador de errores
     * @param clientInterface la interfaz del cliente
     * @param resilience4jConfig la fábrica de instancias de Resilience4j
     */
    public RestHttpClientInvocationHandler(Object httpClient,
                                           RestHttpClient clientConfig,
                                           ErrorHandler errorHandler,
                                           Class<?> clientInterface,
                                           CircuitBreakerUtil resilience4jConfig) {
        this(httpClient, clientConfig, errorHandler, clientInterface, resilience4jConfig, UnaryOperator.identity());
    }

    /**
     * Crea el handler y las instancias de Resilience4j del cliente. Las instancias se nombran con
     * {@code name()} de la anotación o, si está vacío, con el nombre simple de la interfaz.
     *
     * @param httpClient el cliente HTTP (RestClient o RestTemplate)
     * @param clientConfig la configuración del cliente (con sus placeholders ya resueltos)
     * @param errorHandler el manejador de errores
     * @param clientInterface la interfaz del cliente
     * @param resilience4jConfig la fábrica de instancias de Resilience4j
     * @param valueResolver resuelve placeholders en los atributos de mapping y de parámetros
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
        this.valueResolver = valueResolver;
        this.typeMapping = AnnotatedElementUtils.findMergedAnnotation(clientInterface, RequestMapping.class);

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

        RequestMetadata metadata = extractMetadata(method, args);

        return executeWithResilience(metadata, method);
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
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el resultado según el tipo de retorno del método
     * @throws Exception la excepción de la petición
     */
    private Object executeWithResilience(RequestMetadata metadata, Method method) throws Exception {
        Callable<Object> call = () -> executeRequest(metadata, method);

        if (timeLimiter != null) {
            call = withTimeLimit(call);
        }
        if (rateLimiter != null) {
            call = RateLimiter.decorateCallable(rateLimiter, call);
        }
        if (circuitBreaker != null) {
            call = CircuitBreaker.decorateCallable(circuitBreaker, call);
        }
        call = Retry.decorateCallable(isIdempotent(metadata.httpMethod) ? retry : nonIdempotentRetry, call);

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
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el resultado según el tipo de retorno del método
     */
    private Object executeRequest(RequestMetadata metadata, Method method) {
        try {
            if (httpClient instanceof RestClient) {
                return executeWithRestClient(metadata, method);
            } else if (httpClient instanceof RestTemplate) {
                return executeWithRestTemplate(metadata, method);
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
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el resultado según el tipo de retorno del método
     */
    private Object executeWithRestClient(RequestMetadata metadata, Method method) {
        RestClient client = (RestClient) httpClient;

        RestClient.RequestBodySpec bodySpec = client.method(metadata.httpMethod).uri(metadata.uri);

        bodySpec.headers(headers -> headers.addAll(metadata.headers));

        if (metadata.body != null) {
            bodySpec.body(metadata.body);
        }

        RestClient.ResponseSpec responseSpec = bodySpec.retrieve();
        Type bodyType = responseBodyType(method);
        ResponseEntity<?> response = isVoid(bodyType)
                ? responseSpec.toBodilessEntity()
                : responseSpec.toEntity(ParameterizedTypeReference.forType(bodyType));

        return toReturnValue(response, method);
    }

    /**
     * Ejecuta la petición con RestTemplate.
     *
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el resultado según el tipo de retorno del método
     */
    private Object executeWithRestTemplate(RequestMetadata metadata, Method method) {
        RestTemplate template = (RestTemplate) httpClient;

        HttpHeaders headers = new HttpHeaders();
        headers.addAll(metadata.headers);
        HttpEntity<?> entity = new HttpEntity<>(metadata.body, headers);

        Type bodyType = responseBodyType(method);
        ResponseEntity<?> response = isVoid(bodyType)
                ? template.exchange(metadata.uri, metadata.httpMethod, entity, Void.class)
                : template.exchange(metadata.uri, metadata.httpMethod, entity,
                        ParameterizedTypeReference.forType(bodyType));

        return toReturnValue(response, method);
    }

    /**
     * Obtiene el tipo del cuerpo de la respuesta a partir del tipo de retorno genérico del método, para no
     * perder los genéricos (por ejemplo {@code List<User>}). Para {@code ResponseEntity<T>} es {@code T}.
     *
     * @param method el método invocado
     * @return el tipo del cuerpo
     */
    private static Type responseBodyType(Method method) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        if (ResponseEntity.class.isAssignableFrom(returnType.toClass())) {
            ResolvableType bodyType = returnType.as(ResponseEntity.class).getGeneric(0);
            if (bodyType.resolve() == null || bodyType.getType() instanceof WildcardType) {
                return Object.class;
            }
            return bodyType.getType();
        }
        return method.getGenericReturnType();
    }

    /**
     * Indica si el tipo no tiene cuerpo ({@code void} o {@code Void}).
     *
     * @param type el tipo
     * @return true si no se espera cuerpo
     */
    private static boolean isVoid(Type type) {
        return type == void.class || type == Void.class;
    }

    /**
     * Convierte la respuesta al valor que devuelve el método: la propia {@link ResponseEntity}, el cuerpo,
     * o null para {@code void}.
     *
     * @param response la respuesta
     * @param method el método invocado
     * @return el valor de retorno
     * @throws ClientException si el método devuelve un primitivo y la respuesta no tiene cuerpo
     */
    private static Object toReturnValue(ResponseEntity<?> response, Method method) {
        Class<?> returnType = method.getReturnType();
        if (ResponseEntity.class.isAssignableFrom(returnType)) {
            return response;
        }
        if (returnType == void.class) {
            return null;
        }
        Object body = response.getBody();
        if (body == null && returnType.isPrimitive()) {
            throw new ClientException("Empty response body for method " + describe(method)
                    + " returning primitive " + returnType.getName());
        }
        return body;
    }

    /**
     * Construye la URI de la petición ya codificada.
     * Las path variables y los valores de los query params se expanden como variables de plantilla URI,
     * así cada valor se codifica exactamente una vez (incluidos caracteres reservados como '/', '&amp;'
     * o '='). El resultado es un {@link URI}, que RestClient y RestTemplate envían tal cual sin volver
     * a codificarlo.
     *
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return la URI codificada
     * @throws ClientException si falta una variable de la ruta o la URL no es válida
     */
    private URI buildUri(RequestMetadata metadata, Method method) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                    joinPath(clientConfig.url(), metadata.path));
            Map<String, Object> uriVariables = new HashMap<>(metadata.pathVariables);

            int index = 0;
            for (Map.Entry<String, List<String>> entry : metadata.queryParams.entrySet()) {
                for (String value : entry.getValue()) {
                    String variable = QUERY_PARAM_VARIABLE_PREFIX + index++;
                    builder.queryParam(entry.getKey(), "{" + variable + "}");
                    uriVariables.put(variable, value);
                }
            }

            return builder.encode().buildAndExpand(uriVariables).toUri();
        } catch (IllegalArgumentException e) {
            throw new ClientException("Cannot build request URI for " + describe(method) + ": " + e.getMessage(), e);
        }
    }

    /**
     * Une dos tramos de ruta con exactamente una barra entre ellos.
     *
     * @param base el tramo base (por ejemplo la URL del cliente)
     * @param path el tramo a agregar
     * @return la ruta unida
     */
    static String joinPath(String base, String path) {
        if (path.isEmpty()) {
            return base;
        }
        if (base.isEmpty()) {
            return path;
        }
        boolean baseSlash = base.endsWith("/");
        boolean pathSlash = path.startsWith("/");
        if (baseSlash && pathSlash) {
            return base + path.substring(1);
        }
        if (!baseSlash && !pathSlash) {
            return base + "/" + path;
        }
        return base + path;
    }

    /**
     * Extrae los datos de la petición a partir de las anotaciones de la interfaz, del método y de sus
     * argumentos.
     *
     * El {@code @RequestMapping} de la interfaz aporta un prefijo de ruta y valores por defecto de
     * {@code headers}, {@code params}, {@code produces}, {@code consumes} y {@code method}; los del método
     * los complementan o reemplazan. {@code produces} se envía como {@code Accept} y el primer
     * {@code consumes} como {@code Content-Type}; {@code params} con formato {@code nombre=valor} se envía
     * como query params.
     *
     * @param method el método invocado
     * @param args los argumentos
     * @return los datos de la petición
     * @throws ClientException si el método no tiene un mapping válido o falta un valor requerido
     */
    private RequestMetadata extractMetadata(Method method, Object[] args) {
        RequestMetadata metadata = new RequestMetadata();

        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            throw new ClientException("Method " + describe(method)
                    + " must be annotated with @GetMapping, @PostMapping, @PutMapping, "
                    + "@PatchMapping, @DeleteMapping or @RequestMapping(method = ...)");
        }
        metadata.httpMethod = resolveHttpMethod(mapping, method);
        metadata.path = joinPath(singlePath(typeMapping, method), singlePath(mapping, method));

        for (RequestMapping source : new RequestMapping[]{typeMapping, mapping}) {
            if (source == null) {
                continue;
            }
            if (source.produces().length > 0) {
                metadata.headers.set(HttpHeaders.ACCEPT, String.join(", ", resolveAll(source.produces())));
            }
            if (source.consumes().length > 0) {
                metadata.headers.set(HttpHeaders.CONTENT_TYPE, valueResolver.apply(source.consumes()[0]));
            }
            extractNameValuePairs(resolveAll(source.headers()), "headers").forEach(metadata.headers::set);
            extractNameValuePairs(resolveAll(source.params()), "params")
                    .forEach((name, value) -> metadata.queryParams.put(name, new ArrayList<>(List.of(value))));
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            bindParameter(metadata, method, parameters[i], args[i]);
        }

        metadata.uri = buildUri(metadata, method);
        return metadata;
    }

    /**
     * Obtiene el único método HTTP: el del método o, si no declara ninguno, el de la interfaz.
     *
     * @param mapping el mapping del método
     * @param method el método invocado
     * @return el método HTTP
     * @throws ClientException si no hay método HTTP o hay más de uno
     */
    private HttpMethod resolveHttpMethod(RequestMapping mapping, Method method) {
        RequestMethod[] methods = mapping.method();
        if (methods.length == 0 && typeMapping != null) {
            methods = typeMapping.method();
        }
        if (methods.length == 0) {
            throw new ClientException("Method " + describe(method)
                    + " must be annotated with @GetMapping, @PostMapping, @PutMapping, "
                    + "@PatchMapping, @DeleteMapping or @RequestMapping(method = ...)");
        }
        if (methods.length > 1) {
            throw new ClientException("Method " + describe(method) + " declares more than one HTTP method");
        }
        return methods[0].asHttpMethod();
    }

    /**
     * Obtiene la única ruta de un mapping, con sus placeholders resueltos.
     *
     * @param mapping el mapping (puede ser null)
     * @param method el método invocado, para el mensaje de error
     * @return la ruta, o vacío si no declara ninguna
     * @throws ClientException si declara más de una ruta
     */
    private String singlePath(RequestMapping mapping, Method method) {
        if (mapping == null || mapping.path().length == 0) {
            return "";
        }
        if (mapping.path().length > 1) {
            throw new ClientException("Method " + describe(method) + " declares more than one path");
        }
        return valueResolver.apply(mapping.path()[0]);
    }

    /**
     * Asocia un argumento a la petición según las anotaciones de Spring {@link PathVariable},
     * {@link RequestParam}, {@link RequestHeader} y {@link RequestBody}. Se usan anotaciones combinadas
     * (merged) para resolver el alias {@code value}/{@code name}. Los argumentos sin ninguna de estas
     * anotaciones se ignoran. Los parámetros declarados como {@link Map} (incluido {@code MultiValueMap})
     * aportan una entrada por clave.
     *
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @param param el parámetro
     * @param arg el valor del argumento
     * @throws ClientException si falta un valor requerido
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
     * Indica si el parámetro está declarado como {@link Map}.
     *
     * @param param el parámetro
     * @return true para parámetros Map (y MultiValueMap)
     */
    private static boolean isMap(Parameter param) {
        return Map.class.isAssignableFrom(param.getType());
    }

    /**
     * Asocia cada entrada de un argumento Map. Los valores pasan por {@link #toValues(Object)}, así un
     * {@code MultiValueMap} o los valores que son listas producen varios valores; se saltan las entradas
     * con clave {@code null} o sin valores. Solo un mapa {@code null} cuenta como faltante: un mapa vacío
     * no envía nada.
     *
     * @param arg el argumento Map (puede ser null)
     * @param required si el mapa es requerido
     * @param kind el tipo de valor, para el mensaje de error
     * @param param el parámetro
     * @param method el método invocado
     * @param sink recibe el nombre de cada entrada con sus valores
     * @throws ClientException si el mapa es requerido y viene null
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
     * Devuelve el nombre de la anotación o, si está vacío, el nombre del parámetro (requiere compilar
     * con {@code -parameters}).
     *
     * @param annotatedName el nombre declarado en la anotación
     * @param param el parámetro
     * @return el nombre efectivo
     */
    private static String nameOf(String annotatedName, Parameter param) {
        return annotatedName.isEmpty() ? param.getName() : annotatedName;
    }

    /**
     * Convierte un argumento en los valores a enviar: sus valores (ver {@link #toValues(Object)}); si no
     * hay, el valor por defecto de la anotación; si tampoco hay y es opcional, una lista vacía. Un
     * argumento {@code null} y una lista o arreglo vacío se consideran faltantes.
     *
     * El valor por defecto admite placeholders {@code ${...}}.
     *
     * @param arg el argumento
     * @param defaultValue el valor por defecto de la anotación ({@link ValueConstants#DEFAULT_NONE} si no hay)
     * @param required si el valor es requerido
     * @param kind el tipo de valor, para el mensaje de error
     * @param name el nombre del valor
     * @param method el método invocado
     * @return los valores a enviar; vacío para omitirlos
     * @throws ClientException si el valor es requerido y falta
     */
    private List<String> resolveValues(Object arg, String defaultValue, boolean required, String kind,
                                       String name, Method method) {
        List<String> values = toValues(arg);
        if (!values.isEmpty()) {
            return values;
        }
        if (!ValueConstants.DEFAULT_NONE.equals(defaultValue)) {
            return List.of(valueResolver.apply(defaultValue));
        }
        if (required) {
            throw missingValue(kind, name, method);
        }
        return List.of();
    }

    /**
     * Resuelve los placeholders de cada valor.
     *
     * @param values los valores originales
     * @return los valores resueltos
     */
    private String[] resolveAll(String[] values) {
        String[] resolved = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            resolved[i] = valueResolver.apply(values[i]);
        }
        return resolved;
    }

    /**
     * Convierte un argumento en sus valores de texto: uno por elemento para un {@link Iterable} o un
     * arreglo (incluidos los primitivos; se saltan los elementos {@code null}), un único valor en otro
     * caso y ninguno para {@code null}.
     *
     * @param arg el argumento
     * @return los valores
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
     * Agrega el texto de un valor si no es {@code null}.
     *
     * @param values la lista destino
     * @param value el valor
     */
    private static void addIfNotNull(List<String> values, Object value) {
        if (value != null) {
            values.add(String.valueOf(value));
        }
    }

    /**
     * Crea la excepción para un valor requerido que viene {@code null} o vacío.
     *
     * @param kind el tipo de valor
     * @param name el nombre del valor
     * @param method el método invocado
     * @return la excepción
     */
    private static ClientException missingValue(String kind, String name, Method method) {
        return new ClientException("Required " + kind + " '" + name + "' is null or empty in " + describe(method));
    }

    /**
     * Describe un método como {@code Interfaz.metodo} para los mensajes de error.
     *
     * @param method el método
     * @return la descripción
     */
    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * Extrae pares {@code nombre=valor} de los atributos {@code headers} o {@code params} de un mapping.
     *
     * Las expresiones sin valor (por ejemplo {@code "Nombre"}, {@code "!Nombre"} o {@code "Nombre!=valor"})
     * son condiciones del lado del servidor y se ignoran con un warning.
     *
     * @param expressions las expresiones
     * @param attribute el nombre del atributo, para el log
     * @return los pares por nombre, en orden de declaración
     */
    private static Map<String, String> extractNameValuePairs(String[] expressions, String attribute) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (String expression : expressions) {
            String[] parts = expression.split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[0].trim().endsWith("!")) {
                pairs.put(parts[0].trim(), parts[1].trim());
            } else {
                LOG.warn("Ignoring {} expression without a value (expected 'Name=value'): {}", attribute, expression);
            }
        }
        return pairs;
    }

    /**
     * Datos de una petición extraídos de las anotaciones y los argumentos.
     */
    private static class RequestMetadata {

        /** La ruta relativa a la URL base (prefijo de la interfaz + ruta del método). */
        private String path;

        /** El método HTTP. */
        private HttpMethod httpMethod;

        /** La URI final, ya codificada. */
        private URI uri;

        /** Las path variables (si hay varios valores, ya vienen unidos con comas). */
        private final Map<String, Object> pathVariables = new HashMap<>();

        /** Los query params, en el orden en que se declaran; una entrada por valor. */
        private final Map<String, List<String>> queryParams = new LinkedHashMap<>();

        /** Los headers (sin distinguir mayúsculas, con varios valores por nombre). */
        private final HttpHeaders headers = new HttpHeaders();

        /** El cuerpo de la petición. */
        private Object body;

        /**
         * Agrega una path variable; igual que en Spring, varios valores se unen con comas (que luego
         * se codifican).
         *
         * @param name el nombre de la variable
         * @param values los valores
         */
        private void addPathVariable(String name, List<String> values) {
            pathVariables.put(name, String.join(",", values));
        }

        /**
         * Agrega valores a un query param.
         *
         * @param name el nombre del parámetro
         * @param values los valores
         */
        private void addQueryParam(String name, List<String> values) {
            queryParams.computeIfAbsent(name, key -> new ArrayList<>()).addAll(values);
        }

        /**
         * Define un header, reemplazando el valor declarado en el atributo {@code headers} del mapping.
         *
         * @param name el nombre del header
         * @param values los valores
         */
        private void setHeader(String name, List<String> values) {
            headers.remove(name);
            headers.addAll(name, values);
        }
    }
}
