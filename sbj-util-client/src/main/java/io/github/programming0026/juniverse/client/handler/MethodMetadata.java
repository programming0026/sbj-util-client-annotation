package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.MethodConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
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
import java.io.InputStream;
import java.lang.annotation.Annotation;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.BaseStream;

/**
 * What is known about a client method without knowing its arguments: HTTP method, path, fixed headers and
 * params, how each parameter is bound and how the response is read.
 * It is computed only once per method when the proxy is created (the annotations are not read again on each
 * call) and it is validated at that moment: a badly declared method makes the creation of the client fail, that
 * is, the application startup, instead of the first call.
 *
 * @author Java Universe
 */
final class MethodMetadata {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(MethodMetadata.class);

    /** Prefix of the URI template variables generated for the query param values. */
    private static final String QUERY_PARAM_VARIABLE_PREFIX = "__queryParam";

    /** Message for a method without a mapping annotation or without an HTTP method. */
    private static final String MAPPING_REQUIRED = " must be annotated with @GetMapping, @PostMapping, "
            + "@PutMapping, @PatchMapping, @DeleteMapping or @RequestMapping(method = ...)";

    /** The method, as {@code Interface.method}, for the error messages. */
    private final String description;

    /** The HTTP method. */
    private final HttpMethod httpMethod;

    /** The path relative to the base URL (interface prefix + method path), with placeholders resolved. */
    private final String path;

    /** The mapping's fixed headers ({@code produces}, {@code consumes}, {@code headers}). */
    private final HttpHeaders staticHeaders;

    /** The mapping's fixed query params ({@code params}). */
    private final Map<String, List<String>> staticParams;

    /** How each parameter is bound, in the method's order. */
    private final List<ParameterBinding> bindings;

    /** The response body type (with generics). */
    private final Type bodyType;

    /** The body type as a reference for RestClient/RestTemplate, or null if no body is read. */
    private final ParameterizedTypeReference<Object> bodyTypeReference;

    /** The method's return type. */
    private final Class<?> returnType;

    /** The method's own settings ({@code @MethodConfig}). */
    private final MethodSettings settings;

    /**
     * Creates the already validated metadata.
     *
     * @param method the method
     * @param httpMethod the HTTP method
     * @param path the path
     * @param staticHeaders the fixed headers
     * @param staticParams the fixed query params
     * @param bindings the parameter bindings
     * @param settings the method's own settings
     */
    private MethodMetadata(Method method, HttpMethod httpMethod, String path, HttpHeaders staticHeaders,
                           Map<String, List<String>> staticParams, List<ParameterBinding> bindings,
                           MethodSettings settings) {
        this.settings = settings;
        this.description = describe(method);
        this.httpMethod = httpMethod;
        this.path = path;
        this.staticHeaders = staticHeaders;
        this.staticParams = staticParams;
        this.bindings = bindings;
        this.returnType = method.getReturnType();
        this.bodyType = responseBodyType(method);
        this.bodyTypeReference = isBodiless() || isStreaming() ? null : ParameterizedTypeReference.forType(bodyType);
    }

    /**
     * Analyzes and validates a client method.
     * The interface's {@code @RequestMapping} provides a path prefix and default values for
     * {@code headers}, {@code params}, {@code produces}, {@code consumes} and {@code method}; the method's own
     * complement or replace them. {@code produces} is sent as {@code Accept}, the first
     * {@code consumes} as {@code Content-Type} and {@code params} in {@code name=value} format as
     * query params. The {@code ${...}} placeholders are resolved here, only once.
     *
     * @param method the method
     * @param typeMapping the interface's {@code @RequestMapping}, or null
     * @param resolver resolves placeholders
     * @return the method's metadata
     * @throws ClientException if the method is badly declared
     */
    static MethodMetadata of(Method method, RequestMapping typeMapping, UnaryOperator<String> resolver) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            throw new ClientException("Method " + describe(method) + MAPPING_REQUIRED);
        }
        validateReturnType(method);
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

        return new MethodMetadata(method, httpMethod, path, headers, params, List.copyOf(bindings),
                MethodSettings.of(method));
    }

    /**
     * The own settings of a method ({@code @MethodConfig}). Each {@code -1} value means "inherit the client's".
     *
     * @param readTimeout the read timeout in milliseconds
     * @param maxRetries the total number of attempts
     * @param timeLimiterTimeout the maximum time per attempt in milliseconds ({@code 0} = no limit)
     */
    record MethodSettings(long readTimeout, int maxRetries, long timeLimiterTimeout) {

        /** Inherited from the client. */
        static final MethodSettings NONE = new MethodSettings(-1, -1, -1);

        /**
         * Reads and validates the {@code @MethodConfig} of a method.
         *
         * @param method the method
         * @return the settings, or {@link #NONE} if it does not have the annotation
         * @throws ClientException if any value is not valid
         */
        static MethodSettings of(Method method) {
            MethodConfig config = AnnotatedElementUtils.findMergedAnnotation(method, MethodConfig.class);
            if (config == null) {
                return NONE;
            }
            if (config.readTimeout() != -1 && config.readTimeout() <= 0) {
                throw invalidSetting(method, "readTimeout", config.readTimeout(), "greater than 0, or -1 to inherit");
            }
            if (config.maxRetries() != -1 && config.maxRetries() < 1) {
                throw invalidSetting(method, "maxRetries", config.maxRetries(), "at least 1, or -1 to inherit");
            }
            if (config.timeLimiterTimeout() < -1) {
                throw invalidSetting(method, "timeLimiterTimeout", config.timeLimiterTimeout(),
                        "0 (no limit), greater than 0, or -1 to inherit");
            }
            return new MethodSettings(config.readTimeout(), config.maxRetries(), config.timeLimiterTimeout());
        }

        private static ClientException invalidSetting(Method method, String name, long value, String expected) {
            return new ClientException("Method " + describe(method) + " declares @MethodConfig." + name + " = "
                    + value + ", but it must be " + expected);
        }

        /**
         * Indicates whether the method replaces the client's total number of attempts.
         *
         * @return true if {@code maxRetries} is defined
         */
        boolean overridesRetries() {
            return maxRetries != -1;
        }

        /**
         * Indicates whether the method replaces the client's time limiter (with another time or no limit).
         *
         * @return true if {@code timeLimiterTimeout} is defined
         */
        boolean overridesTimeLimiter() {
            return timeLimiterTimeout != -1;
        }
    }

    /**
     * Returns the method's own settings.
     *
     * @return the settings ({@link MethodSettings#NONE} if it has no {@code @MethodConfig})
     */
    MethodSettings settings() {
        return settings;
    }

    /**
     * Validates the parameters against the path: a single {@code @RequestBody}, each named {@code @PathVariable}
     * must exist in the path and each path variable must have its {@code @PathVariable} (unless
     * there is a {@code @PathVariable Map}, which provides them at runtime).
     *
     * @param bindings the parameter bindings
     * @param path the method's path
     * @param method the method
     * @throws ClientException if the parameters are not consistent with the path
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
     * Builds the request of a call from its arguments.
     *
     * @param args the arguments (null if the method has no parameters)
     * @return the request
     * @throws ClientException if a required value is missing
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
     * Sets {@code Content-Type: multipart/form-data} for a method with {@code @RequestPart}. If the mapping
     * already declares a {@code consumes} that is not multipart, the method is badly declared.
     *
     * @param headers the method's fixed headers
     * @param method the method
     * @throws ClientException if the declared {@code consumes} is not {@code multipart/form-data}
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
     * Builds the already encoded request URI.
     * The path variables and the query param values are expanded as URI template variables,
     * so each value is encoded exactly once (including reserved characters such as '/', '&amp;'
     * or '='). The result is a {@link URI}, which RestClient and RestTemplate send as is without encoding it
     * again.
     *
     * @param baseUrl the client's base URL
     * @param request the request
     * @return the encoded URI
     * @throws ClientException if a path variable is missing
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
     * Converts the response into the value the method returns: the {@link ResponseEntity} itself, the body,
     * or null for {@code void}.
     *
     * @param response the response
     * @return the return value
     * @throws ClientException if the method returns a primitive and the response has no body
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
     * Gets the HTTP method.
     *
     * @return the HTTP method
     */
    HttpMethod httpMethod() {
        return httpMethod;
    }

    /**
     * Indicates whether the method returns the body as an {@link InputStream} (or
     * {@code ResponseEntity<InputStream>}): the response is left open and read progressively instead of
     * loading it into memory.
     *
     * @return true if the body is read as a stream
     */
    boolean isStreaming() {
        return bodyType == InputStream.class;
    }

    /**
     * Rejects the return types that the library cannot read, so that the error appears when the client is
     * created and not, with a confusing message, on the first call.
     *
     * @param method the method
     * @throws ClientException if the return type (or the body of a {@code ResponseEntity}) is not supported
     */
    private static void validateReturnType(Method method) {
        Class<?> bodyClass = ResolvableType.forType(responseBodyType(method)).resolve(Object.class);
        for (Class<?> candidate : List.of(method.getReturnType(), bodyClass)) {
            String reason = unsupportedReason(candidate);
            if (reason != null) {
                throw new ClientException("Method " + describe(method) + " returns "
                        + ResolvableType.forMethodReturnType(method) + ", which is not supported: " + reason);
            }
        }
    }

    /**
     * Explains why a return type is not supported.
     *
     * @param type the type
     * @return the reason, or null if the type is supported
     */
    private static String unsupportedReason(Class<?> type) {
        if (Future.class.isAssignableFrom(type) || CompletionStage.class.isAssignableFrom(type)) {
            return "the clients are synchronous. Return the value directly (or ResponseEntity<T>) and call the "
                    + "method from your own executor if you need it to run asynchronously";
        }
        if (isReactive(type)) {
            return "it is a reactive type and this library is imperative (RestClient/RestTemplate). Return the "
                    + "value directly (or ResponseEntity<T>)";
        }
        if (BaseStream.class.isAssignableFrom(type)) {
            return "a Stream cannot be built from a response. Return a List, or an InputStream to read a "
                    + "download progressively";
        }
        if (InputStream.class.isAssignableFrom(type) && type != InputStream.class) {
            return "only InputStream itself is supported, not its subclasses";
        }
        return null;
    }

    /**
     * Indicates whether the type is reactive (Reactor, Reactive Streams, RxJava or Kotlin Flow). It is detected by
     * the name of the class and its supertypes, so as not to depend on those libraries.
     *
     * @param type the type
     * @return true if it is a reactive type
     */
    private static boolean isReactive(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (isReactiveName(current.getName())) {
                return true;
            }
            for (Class<?> implemented : ClassUtils.getAllInterfacesForClass(current)) {
                if (isReactiveName(implemented.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isReactiveName(String name) {
        return name.startsWith("reactor.core.publisher.") || name.equals("org.reactivestreams.Publisher")
                || name.startsWith("io.reactivex.") || name.equals("kotlinx.coroutines.flow.Flow");
    }

    /**
     * Indicates whether the response has no body to read ({@code void} or {@code Void}).
     *
     * @return true if no body is read
     */
    boolean isBodiless() {
        return bodyType == void.class || bodyType == Void.class;
    }

    /**
     * Gets the response body type.
     *
     * @return the type reference, or null if no body is read
     */
    ParameterizedTypeReference<Object> bodyTypeReference() {
        return bodyTypeReference;
    }

    /**
     * Joins two path segments with exactly one slash between them.
     *
     * @param base the base segment (for example the client's URL)
     * @param path the segment to append
     * @return the joined path
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
     * Describes a method as {@code Interface.method} for the error messages.
     *
     * @param method the method
     * @return the description
     */
    static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * Gets the single HTTP method: the method's own or, if it declares none, the interface's.
     *
     * @param mapping the method's mapping
     * @param typeMapping the interface's mapping, or null
     * @param method the method
     * @return the HTTP method
     * @throws ClientException if there is no HTTP method or there is more than one
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
     * Gets the single path of a mapping, with its placeholders resolved.
     *
     * @param mapping the mapping (may be null)
     * @param method the method, for the error message
     * @param resolver resolves placeholders
     * @return the path, or empty if it declares none
     * @throws ClientException if it declares more than one path
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
     * Gets the response body type from the method's generic return type, so as not to lose the generics (for
     * example {@code List<User>}). For {@code ResponseEntity<T>} it is {@code T}.
     *
     * @param method the method
     * @return the body type
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
     * Resolves the placeholders of each value.
     *
     * @param values the original values
     * @param resolver resolves placeholders
     * @return the resolved values
     */
    private static String[] resolveAll(String[] values, UnaryOperator<String> resolver) {
        String[] resolved = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            resolved[i] = resolver.apply(values[i]);
        }
        return resolved;
    }

    /**
     * Extracts {@code name=value} pairs from the {@code headers} or {@code params} attributes of a mapping.
     * Expressions without a value (for example {@code "Name"}, {@code "!Name"} or {@code "Name!=value"})
     * are server-side conditions and are ignored with a warning.
     *
     * @param expressions the expressions
     * @param attribute the attribute name, for the log
     * @return the pairs by name, in declaration order
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
     * How a method parameter is bound to the request.
     *
     * @param kind the parameter's annotation
     * @param name the effective name (annotation or parameter name)
     * @param required whether the value is required
     * @param defaultValue the default value with placeholders resolved, or null if it has none
     * @param map whether the parameter is declared as a {@link Map}
     * @param formats the parameter's {@code @DateTimeFormat} / {@code @NumberFormat} annotations, which format
     *                its text values; empty if it has none
     */
    record ParameterBinding(Kind kind, String name, boolean required, String defaultValue, boolean map,
                            List<Annotation> formats) {

        /** Formats the values that carry {@code @DateTimeFormat} or {@code @NumberFormat}. */
        private static final ConversionService FORMATTING = new DefaultFormattingConversionService();

        /** The target type of every conversion. */
        private static final TypeDescriptor TEXT = TypeDescriptor.valueOf(String.class);

        /** The parameter's Spring Web annotation. */
        enum Kind {
            /** {@code @PathVariable}. */
            PATH_VARIABLE,
            /** {@code @RequestParam}. */
            REQUEST_PARAM,
            /** {@code @RequestHeader}. */
            REQUEST_HEADER,
            /** {@code @CookieValue}: a {@code name=value} pair of the {@code Cookie} header. */
            COOKIE,
            /** {@code @RequestBody}. */
            REQUEST_BODY,
            /** {@code @RequestPart}: a part of a {@code multipart/form-data} request. */
            REQUEST_PART
        }

        /**
         * Analyzes a parameter. Merged annotations are used to resolve the {@code value}/{@code name} alias;
         * if there is no name, the parameter's name is used (requires {@code -parameters}).
         *
         * @param parameter the parameter
         * @param method the method, for the error message
         * @param resolver resolves placeholders of the {@code defaultValue}
         * @return the binding
         * @throws ClientException if the parameter has none of the supported annotations
         */
        static ParameterBinding of(Parameter parameter, Method method, UnaryOperator<String> resolver) {
            boolean map = Map.class.isAssignableFrom(parameter.getType());
            List<Annotation> formats = formatsOf(parameter);
            PathVariable pathVariable = AnnotatedElementUtils.findMergedAnnotation(parameter, PathVariable.class);
            if (pathVariable != null) {
                return new ParameterBinding(Kind.PATH_VARIABLE, nameOf(pathVariable.name(), parameter),
                        pathVariable.required(), null, map, formats);
            }
            RequestParam requestParam = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestParam.class);
            if (requestParam != null) {
                return new ParameterBinding(Kind.REQUEST_PARAM, nameOf(requestParam.name(), parameter),
                        requestParam.required(), defaultOf(requestParam.defaultValue(), resolver), map, formats);
            }
            RequestHeader requestHeader = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestHeader.class);
            if (requestHeader != null) {
                return new ParameterBinding(Kind.REQUEST_HEADER, nameOf(requestHeader.name(), parameter),
                        requestHeader.required(), defaultOf(requestHeader.defaultValue(), resolver), map, formats);
            }
            CookieValue cookieValue = AnnotatedElementUtils.findMergedAnnotation(parameter, CookieValue.class);
            if (cookieValue != null) {
                return new ParameterBinding(Kind.COOKIE, nameOf(cookieValue.name(), parameter),
                        cookieValue.required(), defaultOf(cookieValue.defaultValue(), resolver), map, formats);
            }
            RequestBody requestBody = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestBody.class);
            if (requestBody != null) {
                return new ParameterBinding(Kind.REQUEST_BODY, parameter.getName(), requestBody.required(), null,
                        false, List.of());
            }
            RequestPart requestPart = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestPart.class);
            if (requestPart != null) {
                return new ParameterBinding(Kind.REQUEST_PART, nameOf(requestPart.name(), parameter),
                        requestPart.required(), null, false, List.of());
            }
            throw new ClientException("Parameter '" + parameter.getName() + "' of method " + describe(method)
                    + " must be annotated with @PathVariable, @RequestParam, @RequestHeader, @CookieValue, "
                    + "@RequestBody or @RequestPart");
        }

        /**
         * Binds the argument to the request.
         * With lists, arrays and {@code Map}: a query param repeated per value, a header with several values
         * and path variables joined with a comma. A {@code null} or empty argument uses the default value,
         * is omitted if optional or, if required, throws an error ({@code @PathVariable} always is). For
         * a {@code Map} only {@code null} counts as missing.
         *
         * @param request the request
         * @param arg the argument
         * @param description the method, for the error message
         * @throws ClientException if a required value is missing
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
                case COOKIE -> bindValues(arg, "cookie", description, request::addCookie);
                default -> bindValues(arg, "request header", description, request::setHeader);
            }
        }

        /**
         * Binds a part of a multipart request. A list or array (except {@code byte[]}) provides one
         * part per element with the same name; a {@code null} value or an empty list is omitted if the part
         * is optional and is an error if it is required.
         *
         * @param request the request
         * @param arg the argument
         * @param description the method, for the error message
         * @throws ClientException if a required part is missing
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
         * Converts an argument into the parts it provides: one per element if it is an {@link Iterable} or an
         * array (a {@link Path} and a {@code byte[]} count as a single value), skipping {@code null}s.
         *
         * @param arg the argument
         * @return the parts, already converted with {@link #toPart(Object)}
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
         * Prepares the value of a part for Spring's multipart converter:
         * <ul>
         *   <li>{@link MultipartFile}: sent as a file, with its name and its content type.</li>
         *   <li>{@link File} and {@link Path}: sent as a file with the file name.</li>
         *   <li>{@link Resource} and {@code byte[]}: as is (a {@code Resource} provides its file name).</li>
         *   <li>Texts, numbers, booleans and enums: as text.</li>
         *   <li>Any other object: as an {@code application/json} part.</li>
         * </ul>
         *
         * @param value the value
         * @return the value ready to send
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
         * Binds a query param or header: its values, the default value, nothing if optional or an error if
         * required.
         *
         * @param arg the argument
         * @param label the kind of value, for the error message
         * @param description the method, for the error message
         * @param sink receives the name with its values
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
         * Binds each entry of a Map argument; entries with a {@code null} key or without values are skipped.
         *
         * @param arg the Map argument (may be null)
         * @param label the kind of value, for the error message
         * @param description the method, for the error message
         * @param sink receives the name of each entry with its values
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
         * Creates the exception for a required value that is {@code null} or empty.
         *
         * @param label the kind of value
         * @param description the method
         * @return the exception
         */
        private ClientException missingValue(String label, String description) {
            return new ClientException("Required " + label + " '" + name + "' is null or empty in " + description);
        }

        /**
         * Returns the annotation's name or, if empty, the parameter's name.
         *
         * @param annotatedName the name declared in the annotation
         * @param parameter the parameter
         * @return the effective name
         */
        private static String nameOf(String annotatedName, Parameter parameter) {
            return annotatedName.isEmpty() ? parameter.getName() : annotatedName;
        }

        /**
         * Collects the parameter's format annotations.
         *
         * @param parameter the parameter
         * @return {@code @DateTimeFormat} and {@code @NumberFormat} if it has them
         */
        private static List<Annotation> formatsOf(Parameter parameter) {
            List<Annotation> formats = new ArrayList<>();
            for (Annotation annotation : parameter.getAnnotations()) {
                if (annotation instanceof DateTimeFormat || annotation instanceof NumberFormat) {
                    formats.add(annotation);
                }
            }
            return List.copyOf(formats);
        }

        /**
         * Converts a value into text. With {@code @DateTimeFormat} or {@code @NumberFormat} on the parameter it
         * uses Spring's {@code ConversionService} (pattern, ISO, style...); without them, {@code String.valueOf},
         * which for dates and numbers already gives the standard format (ISO-8601, no thousands separators).
         *
         * @param value the value, not null
         * @return the text that is sent
         */
        private String text(Object value) {
            if (!formats.isEmpty()) {
                TypeDescriptor source = new TypeDescriptor(ResolvableType.forClass(value.getClass()), null,
                        formats.toArray(new Annotation[0]));
                if (FORMATTING.canConvert(source, TEXT)) {
                    Object converted = FORMATTING.convert(value, source, TEXT);
                    if (converted != null) {
                        return converted.toString();
                    }
                }
            }
            return String.valueOf(value);
        }

        /**
         * Resolves the annotation's default value.
         *
         * @param defaultValue the declared value ({@link ValueConstants#DEFAULT_NONE} if there is none)
         * @param resolver resolves placeholders
         * @return the resolved value, or null if there is none
         */
        private static String defaultOf(String defaultValue, UnaryOperator<String> resolver) {
            return ValueConstants.DEFAULT_NONE.equals(defaultValue) ? null : resolver.apply(defaultValue);
        }

        /**
         * Converts an argument into its text values: one per element for an {@link Iterable} or an
         * array (primitives included; {@code null} elements are skipped), a single value otherwise
         * and none for {@code null}.
         *
         * @param arg the argument
         * @return the values
         */
        private List<String> toValues(Object arg) {
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
                values.add(text(arg));
            }
            return values;
        }

        /**
         * Adds the text of a value if it is not {@code null}.
         *
         * @param values the target list
         * @param value the value
         */
        private void addIfNotNull(List<String> values, Object value) {
            if (value != null) {
                values.add(text(value));
            }
        }
    }

    /**
     * Variable data of a call: headers, query params, path variables and body.
     */
    static final class Request {

        /** The headers (case-insensitive, with several values per name). */
        private final HttpHeaders headers = new HttpHeaders();

        /** The query params, in declaration order; one entry per value. */
        private final Map<String, List<String>> queryParams = new LinkedHashMap<>();

        /** The path variables (if there are several values, they are already joined with commas). */
        private final Map<String, Object> pathVariables = new HashMap<>();

        /** The parts of a multipart request, in declaration order. */
        private final MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        /** The request body (with parts, the map of parts). */
        private Object body;

        /**
         * Creates the request starting from the method's fixed headers and params (they are copied).
         *
         * @param staticHeaders the fixed headers
         * @param staticParams the fixed query params
         */
        private Request(HttpHeaders staticHeaders, Map<String, List<String>> staticParams) {
            headers.addAll(staticHeaders);
            staticParams.forEach((name, values) -> queryParams.put(name, new ArrayList<>(values)));
        }

        /**
         * Gets the headers.
         *
         * @return the headers
         */
        HttpHeaders headers() {
            return headers;
        }

        /**
         * Gets the body.
         *
         * @return the body, or null
         */
        Object body() {
            return body;
        }

        /**
         * Adds a multipart part.
         *
         * @param name the part name
         * @param value the already converted value
         */
        private void addPart(String name, Object value) {
            parts.add(name, value);
        }

        /**
         * Adds a path variable; as in Spring, several values are joined with commas (which are then
         * encoded).
         *
         * @param name the variable name
         * @param values the values
         */
        private void addPathVariable(String name, List<String> values) {
            pathVariables.put(name, String.join(",", values));
        }

        /**
         * Adds values to a query param.
         *
         * @param name the parameter name
         * @param values the values
         */
        private void addQueryParam(String name, List<String> values) {
            queryParams.computeIfAbsent(name, key -> new ArrayList<>()).addAll(values);
        }

        /**
         * Sets a header, replacing the value declared in the mapping's {@code headers} attribute.
         *
         * @param name the header name
         * @param values the values
         */
        private void setHeader(String name, List<String> values) {
            headers.remove(name);
            headers.addAll(name, values);
        }

        /**
         * Adds cookies to the {@code Cookie} header: one {@code name=value} pair per value, separated by
         * {@code "; "} and after the cookies the header already had (for example, those of the mapping's
         * {@code headers} attribute). The value is sent as is, without encoding.
         *
         * @param name the cookie name
         * @param values the values
         * @throws ClientException if a value contains {@code ;} or line breaks, which would break the header
         */
        private void addCookie(String name, List<String> values) {
            StringBuilder cookie = new StringBuilder();
            String existing = headers.getFirst(HttpHeaders.COOKIE);
            if (existing != null && !existing.isBlank()) {
                cookie.append(existing);
            }
            for (String value : values) {
                if (value.indexOf(';') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                    throw new ClientException("The value of cookie '" + name
                            + "' cannot contain ';' or line breaks");
                }
                if (!cookie.isEmpty()) {
                    cookie.append("; ");
                }
                cookie.append(name).append('=').append(value);
            }
            headers.set(HttpHeaders.COOKIE, cookie.toString());
        }
    }
}
