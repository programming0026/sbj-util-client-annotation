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
 * Atiende cada llamada al proxy de un cliente {@link RestHttpClient}.
 *
 * Lee las anotaciones de Spring Web del método y de sus parámetros, arma la petición, la ejecuta con
 * RestClient o RestTemplate y la envuelve con los decoradores de Resilience4j en este orden:
 * Retry (siempre) → CircuitBreaker → RateLimiter → TimeLimiter (el más externo).
 */
public class RestHttpClientInvocationHandler implements InvocationHandler {
    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(RestHttpClientInvocationHandler.class);

    /** Prefijo de las variables de plantilla URI que se generan para los valores de query params. */
    private static final String QUERY_PARAM_VARIABLE_PREFIX = "__queryParam";

    /** El cliente HTTP: un RestClient o un RestTemplate. */
    private final Object httpClient;

    /** La configuración del cliente. */
    private final RestHttpClient clientConfig;

    /** El manejador de errores. */
    private final ErrorHandler errorHandler;

    /** El circuit breaker, o null si está deshabilitado. */
    private final CircuitBreaker circuitBreaker;

    /** El retry (siempre presente). */
    private final Retry retry;

    /** El rate limiter, o null si está deshabilitado. */
    private final RateLimiter rateLimiter;

    /** El time limiter, o null si está deshabilitado. */
    private final TimeLimiter timeLimiter;

    /**
     * Crea el handler y las instancias de Resilience4j del cliente. Las instancias se nombran con
     * {@code name()} de la anotación o, si está vacío, con el nombre simple de la interfaz.
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
     * Atiende una llamada al proxy. Los métodos de {@link Object} (toString, equals, hashCode) se
     * ejecutan sobre este handler.
     *
     * @param proxy el proxy
     * @param method el método invocado
     * @param args los argumentos
     * @return el cuerpo de la respuesta, convertido al tipo de retorno del método
     * @throws Throwable la excepción de la petición
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
     * Ejecuta la petición envuelta con los decoradores de Resilience4j habilitados.
     *
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el cuerpo de la respuesta
     * @throws Exception la excepción de la petición
     * @throws ClientException si se excede el tiempo del time limiter
     */
    private Object executeWithResilience(RequestMetadata metadata, Method method) throws Exception {

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
                throw new ClientException("Request timed out after "
                        + clientConfig.timeLimiter().timeout() + " ms", e);
            }
        }

        return decoratedSupplier.get();
    }

    /**
     * Ejecuta la petición con el cliente HTTP configurado. Ante un error HTTP, primero consulta al
     * {@link ErrorHandler}; si no lanza su propia excepción, se propaga la original.
     *
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el cuerpo de la respuesta
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
     * Ejecuta la petición con RestClient.
     *
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el cuerpo de la respuesta
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
     * Ejecuta la petición con RestTemplate.
     *
     * @param metadata los datos de la petición
     * @param method el método invocado
     * @return el cuerpo de la respuesta
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
     * Construye la URI de la petición ya codificada.
     * Las path variables y los valores de los query params se expanden como variables de plantilla URI,
     * así cada valor se codifica exactamente una vez (incluidos caracteres reservados como '/', '&amp;'
     * o '='). El resultado es un {@link URI}, que RestClient y RestTemplate envían tal cual sin volver
     * a codificarlo.
     *
     * @param metadata los datos de la petición
     * @return la URI codificada
     */
    private URI buildUri(RequestMetadata metadata) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(clientConfig.url() + metadata.path);
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
    }

    /**
     * Extrae los datos de la petición a partir de las anotaciones del método y de sus argumentos.
     * Del mapping se usan el primer {@code path} y el primer {@code method}; {@code produces} se envía
     * como {@code Accept} y el primer {@code consumes} como {@code Content-Type}.
     *
     * @param method el método invocado
     * @param args los argumentos
     * @return los datos de la petición
     * @throws ClientException si el método no tiene una anotación de mapping con método HTTP
     */
    private RequestMetadata extractMetadata(Method method, Object[] args) {
        RequestMetadata metadata = new RequestMetadata();

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
     * @param arg el argumento
     * @param defaultValue el valor por defecto de la anotación ({@link ValueConstants#DEFAULT_NONE} si no hay)
     * @param required si el valor es requerido
     * @param kind el tipo de valor, para el mensaje de error
     * @param name el nombre del valor
     * @param method el método invocado
     * @return los valores a enviar; vacío para omitirlos
     * @throws ClientException si el valor es requerido y falta
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
        return new ClientException("Required " + kind + " '" + name + "' is null or empty in "
                + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }

    /**
     * Extrae los headers del atributo {@code headers} del mapping.
     *
     * Usa el formato de Spring {@code "Nombre=valor"}. Las expresiones sin valor (por ejemplo
     * {@code "Nombre"} o {@code "!Nombre"}) son condiciones del lado del servidor y se ignoran.
     *
     * @param headerArray las expresiones de headers
     * @return los headers por nombre
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
     * Convierte el método HTTP al {@link HttpMethod} de Spring.
     *
     * @param method el método HTTP
     * @return el método HTTP de Spring
     */
    private HttpMethod convertToSpringHttpMethod(HttpMethod method) {
        return HttpMethod.valueOf(method.name());
    }

    /**
     * Datos de una petición extraídos de las anotaciones y los argumentos.
     */
    private static class RequestMetadata {

        /** La ruta relativa a la URL base. */
        private String path;

        /** El método HTTP. */
        private HttpMethod httpMethod;

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
