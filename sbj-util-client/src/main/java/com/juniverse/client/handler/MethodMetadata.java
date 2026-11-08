package com.juniverse.client.handler;

import com.juniverse.client.exception.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * Lo que se sabe de un método de un cliente sin conocer sus argumentos: método HTTP, ruta, headers y params
 * fijos, cómo se enlaza cada parámetro y cómo se lee la respuesta.
 *
 * Se calcula una sola vez por método al crear el proxy (las anotaciones no se vuelven a leer en cada
 * llamada) y en ese momento se valida: un método mal declarado hace fallar la creación del cliente, es decir,
 * el arranque de la aplicación, en vez de la primera llamada.
 */
final class MethodMetadata {

    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(MethodMetadata.class);

    /** Prefijo de las variables de plantilla URI que se generan para los valores de query params. */
    private static final String QUERY_PARAM_VARIABLE_PREFIX = "__queryParam";

    /** Mensaje para un método sin anotación de mapping o sin método HTTP. */
    private static final String MAPPING_REQUIRED = " must be annotated with @GetMapping, @PostMapping, "
            + "@PutMapping, @PatchMapping, @DeleteMapping or @RequestMapping(method = ...)";

    /** El método, como {@code Interfaz.metodo}, para los mensajes de error. */
    private final String description;

    /** El método HTTP. */
    private final HttpMethod httpMethod;

    /** La ruta relativa a la URL base (prefijo de la interfaz + ruta del método), con placeholders resueltos. */
    private final String path;

    /** Los headers fijos del mapping ({@code produces}, {@code consumes}, {@code headers}). */
    private final HttpHeaders staticHeaders;

    /** Los query params fijos del mapping ({@code params}). */
    private final Map<String, List<String>> staticParams;

    /** Cómo se enlaza cada parámetro, en el orden del método. */
    private final List<ParameterBinding> bindings;

    /** El tipo del cuerpo de la respuesta (con genéricos). */
    private final Type bodyType;

    /** El tipo del cuerpo como referencia para RestClient/RestTemplate, o null si no se lee cuerpo. */
    private final ParameterizedTypeReference<Object> bodyTypeReference;

    /** El tipo de retorno del método. */
    private final Class<?> returnType;

    /**
     * Crea los metadatos ya validados.
     *
     * @param method el método
     * @param httpMethod el método HTTP
     * @param path la ruta
     * @param staticHeaders los headers fijos
     * @param staticParams los query params fijos
     * @param bindings los enlaces de parámetros
     */
    private MethodMetadata(Method method, HttpMethod httpMethod, String path, HttpHeaders staticHeaders,
                           Map<String, List<String>> staticParams, List<ParameterBinding> bindings) {
        this.description = describe(method);
        this.httpMethod = httpMethod;
        this.path = path;
        this.staticHeaders = staticHeaders;
        this.staticParams = staticParams;
        this.bindings = bindings;
        this.returnType = method.getReturnType();
        this.bodyType = responseBodyType(method);
        this.bodyTypeReference = isBodiless() ? null : ParameterizedTypeReference.forType(bodyType);
    }

    /**
     * Analiza y valida un método del cliente.
     *
     * El {@code @RequestMapping} de la interfaz aporta un prefijo de ruta y valores por defecto de
     * {@code headers}, {@code params}, {@code produces}, {@code consumes} y {@code method}; los del método
     * los complementan o reemplazan. {@code produces} se envía como {@code Accept}, el primer
     * {@code consumes} como {@code Content-Type} y {@code params} con formato {@code nombre=valor} como
     * query params. Los placeholders {@code ${...}} se resuelven aquí, una sola vez.
     *
     * @param method el método
     * @param typeMapping el {@code @RequestMapping} de la interfaz, o null
     * @param resolver resuelve placeholders
     * @return los metadatos del método
     * @throws ClientException si el método está mal declarado
     */
    static MethodMetadata of(Method method, RequestMapping typeMapping, UnaryOperator<String> resolver) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            throw new ClientException("Method " + describe(method) + MAPPING_REQUIRED);
        }
        HttpMethod httpMethod = resolveHttpMethod(mapping, typeMapping, method);
        String path = joinPath(singlePath(typeMapping, method, resolver), singlePath(mapping, method, resolver));

        HttpHeaders headers = new HttpHeaders();
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (RequestMapping source : new RequestMapping[]{typeMapping, mapping}) {
            if (source == null) {
                continue;
            }
            if (source.produces().length > 0) {
                headers.set(HttpHeaders.ACCEPT, String.join(", ", resolveAll(source.produces(), resolver)));
            }
            if (source.consumes().length > 0) {
                headers.set(HttpHeaders.CONTENT_TYPE, resolver.apply(source.consumes()[0]));
            }
            extractNameValuePairs(resolveAll(source.headers(), resolver), "headers").forEach(headers::set);
            extractNameValuePairs(resolveAll(source.params(), resolver), "params")
                    .forEach((name, value) -> params.put(name, List.of(value)));
        }

        List<ParameterBinding> bindings = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            bindings.add(ParameterBinding.of(parameter, method, resolver));
        }
        validateBindings(bindings, path, method);
        if (bindings.stream().anyMatch(b -> b.kind() == ParameterBinding.Kind.REQUEST_PART)) {
            applyMultipartContentType(headers, method);
        }

        return new MethodMetadata(method, httpMethod, path, headers, params, List.copyOf(bindings));
    }

    /**
     * Valida los parámetros contra la ruta: un solo {@code @RequestBody}, cada {@code @PathVariable} con
     * nombre debe existir en la ruta y cada variable de la ruta debe tener su {@code @PathVariable} (salvo
     * que haya un {@code @PathVariable Map}, que las aporta en tiempo de ejecución).
     *
     * @param bindings los enlaces de parámetros
     * @param path la ruta del método
     * @param method el método
     * @throws ClientException si los parámetros no son coherentes con la ruta
     */
    private static void validateBindings(List<ParameterBinding> bindings, String path, Method method) {
        long bodies = bindings.stream().filter(b -> b.kind() == ParameterBinding.Kind.REQUEST_BODY).count();
        boolean hasParts = bindings.stream().anyMatch(b -> b.kind() == ParameterBinding.Kind.REQUEST_PART);
        if (hasParts && bodies > 0) {
            throw new ClientException("Method " + describe(method) + " cannot combine @RequestBody with @RequestPart");
        }
        if (bodies > 1) {
            throw new ClientException("Method " + describe(method) + " declares more than one @RequestBody");
        }
        Set<String> templateVariables = new LinkedHashSet<>(new UriTemplate(path).getVariableNames());
        boolean dynamicPathVariables = false;
        Set<String> boundVariables = new LinkedHashSet<>();
        for (ParameterBinding binding : bindings) {
            if (binding.kind() != ParameterBinding.Kind.PATH_VARIABLE) {
                continue;
            }
            if (binding.map()) {
                dynamicPathVariables = true;
            } else if (!templateVariables.contains(binding.name())) {
                throw new ClientException("@PathVariable '" + binding.name() + "' of method " + describe(method)
                        + " does not appear in path '" + path + "'");
            } else {
                boundVariables.add(binding.name());
            }
        }
        if (!dynamicPathVariables) {
            templateVariables.removeAll(boundVariables);
            if (!templateVariables.isEmpty()) {
                throw new ClientException("Path variable '" + templateVariables.iterator().next() + "' of method "
                        + describe(method) + " is not bound to any @PathVariable parameter");
            }
        }
    }

    /**
     * Arma la petición de una llamada a partir de sus argumentos.
     *
     * @param args los argumentos (null si el método no tiene parámetros)
     * @return la petición
     * @throws ClientException si falta un valor requerido
     */
    Request bind(Object[] args) {
        Request request = new Request(staticHeaders, staticParams);
        for (int i = 0; i < bindings.size(); i++) {
            bindings.get(i).bind(request, args[i], description);
        }
        if (!request.parts.isEmpty()) {
            request.body = request.parts;
        }
        return request;
    }

    /**
     * Fija {@code Content-Type: multipart/form-data} para un método con {@code @RequestPart}. Si el mapping ya
     * declara un {@code consumes} que no es multipart, el método está mal declarado.
     *
     * @param headers los headers fijos del método
     * @param method el método
     * @throws ClientException si el {@code consumes} declarado no es {@code multipart/form-data}
     */
    private static void applyMultipartContentType(HttpHeaders headers, Method method) {
        String declared = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (declared != null) {
            boolean multipart;
            try {
                multipart = MediaType.parseMediaType(declared).isCompatibleWith(MediaType.MULTIPART_FORM_DATA);
            } catch (IllegalArgumentException e) {
                multipart = false;
            }
            if (!multipart) {
                throw new ClientException("Method " + describe(method) + " has @RequestPart parameters, so it must "
                        + "consume multipart/form-data, but declares '" + declared + "'");
            }
            return;
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    }

    /**
     * Construye la URI de la petición ya codificada.
     *
     * Las path variables y los valores de los query params se expanden como variables de plantilla URI,
     * así cada valor se codifica exactamente una vez (incluidos caracteres reservados como '/', '&amp;'
     * o '='). El resultado es un {@link URI}, que RestClient y RestTemplate envían tal cual sin volver
     * a codificarlo.
     *
     * @param baseUrl la URL base del cliente
     * @param request la petición
     * @return la URI codificada
     * @throws ClientException si falta una variable de la ruta
     */
    URI uri(String baseUrl, Request request) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(joinPath(baseUrl, path));
            Map<String, Object> uriVariables = new HashMap<>(request.pathVariables);

            int index = 0;
            for (Map.Entry<String, List<String>> entry : request.queryParams.entrySet()) {
                for (String value : entry.getValue()) {
                    String variable = QUERY_PARAM_VARIABLE_PREFIX + index++;
                    builder.queryParam(entry.getKey(), "{" + variable + "}");
                    uriVariables.put(variable, value);
                }
            }

            return builder.encode().buildAndExpand(uriVariables).toUri();
        } catch (IllegalArgumentException e) {
            throw new ClientException("Cannot build request URI for " + description + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convierte la respuesta al valor que devuelve el método: la propia {@link ResponseEntity}, el cuerpo,
     * o null para {@code void}.
     *
     * @param response la respuesta
     * @return el valor de retorno
     * @throws ClientException si el método devuelve un primitivo y la respuesta no tiene cuerpo
     */
    Object toReturnValue(ResponseEntity<?> response) {
        if (ResponseEntity.class.isAssignableFrom(returnType)) {
            return response;
        }
        if (returnType == void.class) {
            return null;
        }
        Object body = response.getBody();
        if (body == null && returnType.isPrimitive()) {
            throw new ClientException("Empty response body for method " + description
                    + " returning primitive " + returnType.getName());
        }
        return body;
    }

    /**
     * Obtiene el método HTTP.
     *
     * @return el método HTTP
     */
    HttpMethod httpMethod() {
        return httpMethod;
    }

    /**
     * Indica si la respuesta no tiene cuerpo que leer ({@code void} o {@code Void}).
     *
     * @return true si no se lee cuerpo
     */
    boolean isBodiless() {
        return bodyType == void.class || bodyType == Void.class;
    }

    /**
     * Obtiene el tipo del cuerpo de la respuesta.
     *
     * @return la referencia de tipo, o null si no se lee cuerpo
     */
    ParameterizedTypeReference<Object> bodyTypeReference() {
        return bodyTypeReference;
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
     * Describe un método como {@code Interfaz.metodo} para los mensajes de error.
     *
     * @param method el método
     * @return la descripción
     */
    static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * Obtiene el único método HTTP: el del método o, si no declara ninguno, el de la interfaz.
     *
     * @param mapping el mapping del método
     * @param typeMapping el mapping de la interfaz, o null
     * @param method el método
     * @return el método HTTP
     * @throws ClientException si no hay método HTTP o hay más de uno
     */
    private static HttpMethod resolveHttpMethod(RequestMapping mapping, RequestMapping typeMapping, Method method) {
        RequestMethod[] methods = mapping.method();
        if (methods.length == 0 && typeMapping != null) {
            methods = typeMapping.method();
        }
        if (methods.length == 0) {
            throw new ClientException("Method " + describe(method) + MAPPING_REQUIRED);
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
     * @param method el método, para el mensaje de error
     * @param resolver resuelve placeholders
     * @return la ruta, o vacío si no declara ninguna
     * @throws ClientException si declara más de una ruta
     */
    private static String singlePath(RequestMapping mapping, Method method, UnaryOperator<String> resolver) {
        if (mapping == null || mapping.path().length == 0) {
            return "";
        }
        if (mapping.path().length > 1) {
            throw new ClientException("Method " + describe(method) + " declares more than one path");
        }
        return resolver.apply(mapping.path()[0]);
    }

    /**
     * Obtiene el tipo del cuerpo de la respuesta a partir del tipo de retorno genérico del método, para no
     * perder los genéricos (por ejemplo {@code List<User>}). Para {@code ResponseEntity<T>} es {@code T}.
     *
     * @param method el método
     * @return el tipo del cuerpo
     */
    private static Type responseBodyType(Method method) {
        ResolvableType type = ResolvableType.forMethodReturnType(method);
        if (ResponseEntity.class.isAssignableFrom(type.toClass())) {
            ResolvableType body = type.as(ResponseEntity.class).getGeneric(0);
            if (body.resolve() == null || body.getType() instanceof WildcardType) {
                return Object.class;
            }
            return body.getType();
        }
        return method.getGenericReturnType();
    }

    /**
     * Resuelve los placeholders de cada valor.
     *
     * @param values los valores originales
     * @param resolver resuelve placeholders
     * @return los valores resueltos
     */
    private static String[] resolveAll(String[] values, UnaryOperator<String> resolver) {
        String[] resolved = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            resolved[i] = resolver.apply(values[i]);
        }
        return resolved;
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
     * Cómo se enlaza un parámetro del método a la petición.
     *
     * @param kind la anotación del parámetro
     * @param name el nombre efectivo (anotación o nombre del parámetro)
     * @param required si el valor es requerido
     * @param defaultValue el valor por defecto con placeholders resueltos, o null si no tiene
     * @param map si el parámetro está declarado como {@link Map}
     */
    record ParameterBinding(Kind kind, String name, boolean required, String defaultValue, boolean map) {

        /** La anotación de Spring Web del parámetro. */
        enum Kind {
            /** {@code @PathVariable}. */
            PATH_VARIABLE,
            /** {@code @RequestParam}. */
            REQUEST_PARAM,
            /** {@code @RequestHeader}. */
            REQUEST_HEADER,
            /** {@code @RequestBody}. */
            REQUEST_BODY,
            /** {@code @RequestPart}: una parte de una petición {@code multipart/form-data}. */
            REQUEST_PART
        }

        /**
         * Analiza un parámetro. Se usan anotaciones combinadas (merged) para resolver el alias
         * {@code value}/{@code name}; si no hay nombre se usa el del parámetro (requiere {@code -parameters}).
         *
         * @param parameter el parámetro
         * @param method el método, para el mensaje de error
         * @param resolver resuelve placeholders del {@code defaultValue}
         * @return el enlace
         * @throws ClientException si el parámetro no tiene ninguna de las anotaciones admitidas
         */
        static ParameterBinding of(Parameter parameter, Method method, UnaryOperator<String> resolver) {
            boolean map = Map.class.isAssignableFrom(parameter.getType());
            PathVariable pathVariable = AnnotatedElementUtils.findMergedAnnotation(parameter, PathVariable.class);
            if (pathVariable != null) {
                return new ParameterBinding(Kind.PATH_VARIABLE, nameOf(pathVariable.name(), parameter),
                        pathVariable.required(), null, map);
            }
            RequestParam requestParam = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestParam.class);
            if (requestParam != null) {
                return new ParameterBinding(Kind.REQUEST_PARAM, nameOf(requestParam.name(), parameter),
                        requestParam.required(), defaultOf(requestParam.defaultValue(), resolver), map);
            }
            RequestHeader requestHeader = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestHeader.class);
            if (requestHeader != null) {
                return new ParameterBinding(Kind.REQUEST_HEADER, nameOf(requestHeader.name(), parameter),
                        requestHeader.required(), defaultOf(requestHeader.defaultValue(), resolver), map);
            }
            RequestBody requestBody = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestBody.class);
            if (requestBody != null) {
                return new ParameterBinding(Kind.REQUEST_BODY, parameter.getName(), requestBody.required(), null,
                        false);
            }
            RequestPart requestPart = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestPart.class);
            if (requestPart != null) {
                return new ParameterBinding(Kind.REQUEST_PART, nameOf(requestPart.name(), parameter),
                        requestPart.required(), null, false);
            }
            throw new ClientException("Parameter '" + parameter.getName() + "' of method " + describe(method)
                    + " must be annotated with @PathVariable, @RequestParam, @RequestHeader, @RequestBody or "
                    + "@RequestPart");
        }

        /**
         * Enlaza el argumento a la petición.
         *
         * Con listas, arreglos y {@code Map}: un query param repetido por valor, un header con varios valores
         * y las path variables unidas con coma. Un argumento {@code null} o vacío usa el valor por defecto,
         * se omite si es opcional o, si es requerido, lanza error ({@code @PathVariable} siempre lo es). Para
         * un {@code Map} solo cuenta como faltante el {@code null}.
         *
         * @param request la petición
         * @param arg el argumento
         * @param description el método, para el mensaje de error
         * @throws ClientException si falta un valor requerido
         */
        void bind(Request request, Object arg, String description) {
            switch (kind) {
                case REQUEST_BODY -> {
                    if (arg == null && required) {
                        throw missingValue("request body", description);
                    }
                    request.body = arg;
                }
                case PATH_VARIABLE -> {
                    if (map) {
                        bindMapEntries(arg, "path variable map", description, request::addPathVariable);
                        return;
                    }
                    List<String> values = toValues(arg);
                    if (values.isEmpty()) {
                        throw missingValue("path variable", description);
                    }
                    request.addPathVariable(name, values);
                }
                case REQUEST_PART -> bindPart(request, arg, description);
                case REQUEST_PARAM -> bindValues(arg, "request parameter", description, request::addQueryParam);
                default -> bindValues(arg, "request header", description, request::setHeader);
            }
        }

        /**
         * Enlaza una parte de una petición multipart. Una lista o arreglo (salvo {@code byte[]}) aporta una
         * parte por elemento con el mismo nombre; un valor {@code null} o una lista vacía se omite si la parte
         * es opcional y es un error si es requerida.
         *
         * @param request la petición
         * @param arg el argumento
         * @param description el método, para el mensaje de error
         * @throws ClientException si falta una parte requerida
         */
        private void bindPart(Request request, Object arg, String description) {
            List<Object> values = partValues(arg);
            if (values.isEmpty()) {
                if (required) {
                    throw missingValue("request part", description);
                }
                return;
            }
            values.forEach(value -> request.addPart(name, value));
        }

        /**
         * Convierte un argumento en las partes que aporta: una por elemento si es un {@link Iterable} o un
         * arreglo (un {@link Path} y un {@code byte[]} cuentan como un único valor), omitiendo los {@code null}.
         *
         * @param arg el argumento
         * @return las partes, ya convertidas con {@link #toPart(Object)}
         */
        private static List<Object> partValues(Object arg) {
            List<Object> parts = new ArrayList<>();
            if (arg == null) {
                return parts;
            }
            if (!(arg instanceof Path) && arg instanceof Iterable<?> iterable) {
                iterable.forEach(element -> addPart(parts, element));
            } else if (arg.getClass().isArray() && !(arg instanceof byte[])) {
                for (int i = 0; i < Array.getLength(arg); i++) {
                    addPart(parts, Array.get(arg, i));
                }
            } else {
                addPart(parts, arg);
            }
            return parts;
        }

        private static void addPart(List<Object> parts, Object value) {
            if (value != null) {
                parts.add(toPart(value));
            }
        }

        /**
         * Prepara el valor de una parte para el conversor multipart de Spring:
         * <ul>
         *   <li>{@link MultipartFile}: se envía como archivo, con su nombre y su tipo de contenido.</li>
         *   <li>{@link File} y {@link Path}: se envían como archivo con el nombre del archivo.</li>
         *   <li>{@link Resource} y {@code byte[]}: tal cual (un {@code Resource} aporta su nombre de archivo).</li>
         *   <li>Textos, números, booleanos y enums: como texto.</li>
         *   <li>Cualquier otro objeto: como una parte {@code application/json}.</li>
         * </ul>
         *
         * @param value el valor
         * @return el valor listo para enviar
         */
        private static Object toPart(Object value) {
            if (value instanceof MultipartFile file) {
                HttpHeaders partHeaders = new HttpHeaders();
                if (file.getContentType() != null && !file.getContentType().isBlank()) {
                    partHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
                }
                return new HttpEntity<>(file.getResource(), partHeaders);
            }
            if (value instanceof File file) {
                return new FileSystemResource(file);
            }
            if (value instanceof Path path) {
                return new FileSystemResource(path);
            }
            if (value instanceof Resource || value instanceof byte[] || value instanceof HttpEntity<?>
                    || value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                    || value instanceof Character || value instanceof Enum<?>) {
                return value;
            }
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(value, jsonHeaders);
        }

        /**
         * Enlaza un query param o header: sus valores, el valor por defecto, nada si es opcional o error si es
         * requerido.
         *
         * @param arg el argumento
         * @param label el tipo de valor, para el mensaje de error
         * @param description el método, para el mensaje de error
         * @param sink recibe el nombre con sus valores
         */
        private void bindValues(Object arg, String label, String description, BiConsumer<String, List<String>> sink) {
            if (map) {
                bindMapEntries(arg, label + " map", description, sink);
                return;
            }
            List<String> values = toValues(arg);
            if (values.isEmpty() && defaultValue != null) {
                values = List.of(defaultValue);
            }
            if (values.isEmpty()) {
                if (required) {
                    throw missingValue(label, description);
                }
                return;
            }
            sink.accept(name, values);
        }

        /**
         * Enlaza cada entrada de un argumento Map; se saltan las entradas con clave {@code null} o sin valores.
         *
         * @param arg el argumento Map (puede ser null)
         * @param label el tipo de valor, para el mensaje de error
         * @param description el método, para el mensaje de error
         * @param sink recibe el nombre de cada entrada con sus valores
         */
        private void bindMapEntries(Object arg, String label, String description,
                                    BiConsumer<String, List<String>> sink) {
            if (arg == null) {
                if (required) {
                    throw missingValue(label, description);
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
         * Crea la excepción para un valor requerido que viene {@code null} o vacío.
         *
         * @param label el tipo de valor
         * @param description el método
         * @return la excepción
         */
        private ClientException missingValue(String label, String description) {
            return new ClientException("Required " + label + " '" + name + "' is null or empty in " + description);
        }

        /**
         * Devuelve el nombre de la anotación o, si está vacío, el nombre del parámetro.
         *
         * @param annotatedName el nombre declarado en la anotación
         * @param parameter el parámetro
         * @return el nombre efectivo
         */
        private static String nameOf(String annotatedName, Parameter parameter) {
            return annotatedName.isEmpty() ? parameter.getName() : annotatedName;
        }

        /**
         * Resuelve el valor por defecto de la anotación.
         *
         * @param defaultValue el valor declarado ({@link ValueConstants#DEFAULT_NONE} si no hay)
         * @param resolver resuelve placeholders
         * @return el valor resuelto, o null si no hay
         */
        private static String defaultOf(String defaultValue, UnaryOperator<String> resolver) {
            return ValueConstants.DEFAULT_NONE.equals(defaultValue) ? null : resolver.apply(defaultValue);
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
    }

    /**
     * Datos variables de una llamada: headers, query params, path variables y cuerpo.
     */
    static final class Request {

        /** Los headers (sin distinguir mayúsculas, con varios valores por nombre). */
        private final HttpHeaders headers = new HttpHeaders();

        /** Los query params, en el orden en que se declaran; una entrada por valor. */
        private final Map<String, List<String>> queryParams = new LinkedHashMap<>();

        /** Las path variables (si hay varios valores, ya vienen unidos con comas). */
        private final Map<String, Object> pathVariables = new HashMap<>();

        /** Las partes de una petición multipart, en el orden en que se declaran. */
        private final MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        /** El cuerpo de la petición (con partes, el mapa de partes). */
        private Object body;

        /**
         * Crea la petición partiendo de los headers y params fijos del método (se copian).
         *
         * @param staticHeaders los headers fijos
         * @param staticParams los query params fijos
         */
        private Request(HttpHeaders staticHeaders, Map<String, List<String>> staticParams) {
            headers.addAll(staticHeaders);
            staticParams.forEach((name, values) -> queryParams.put(name, new ArrayList<>(values)));
        }

        /**
         * Obtiene los headers.
         *
         * @return los headers
         */
        HttpHeaders headers() {
            return headers;
        }

        /**
         * Obtiene el cuerpo.
         *
         * @return el cuerpo, o null
         */
        Object body() {
            return body;
        }

        /**
         * Agrega una parte multipart.
         *
         * @param name el nombre de la parte
         * @param value el valor ya convertido
         */
        private void addPart(String name, Object value) {
            parts.add(name, value);
        }

        /**
         * Agrega una path variable; igual que en Spring, varios valores se unen con comas (que luego se
         * codifican).
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
