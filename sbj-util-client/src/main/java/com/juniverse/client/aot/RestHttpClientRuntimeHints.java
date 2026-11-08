package com.juniverse.client.aot;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.CircuitBreakerConfig;
import com.juniverse.client.autoconfigure.MethodConfig;
import com.juniverse.client.autoconfigure.RateLimiterConfig;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.autoconfigure.SSLConfig;
import com.juniverse.client.autoconfigure.TimeLimiterConfig;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.core.ResolvableType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registra los hints de GraalVM (proxies y reflexión) que necesita un cliente {@code @RestHttpClient}.
 *
 * En la JVM la librería descubre todo por reflexión: crea un proxy JDK de la interfaz, lee sus métodos,
 * parámetros y anotaciones, crea copias sintetizadas de las anotaciones (para resolver los placeholders),
 * instancia el manejador de errores, los interceptores y el fallback, y Jackson enlaza los tipos de retorno y
 * de cuerpo. En una imagen nativa nada de eso existe si no se declara antes.
 */
public final class RestHttpClientRuntimeHints {

    /** Registra los tipos que Jackson enlaza. */
    private static final BindingReflectionHintsRegistrar BINDING = new BindingReflectionHintsRegistrar();

    /**
     * Clase de utilidades: no se instancia.
     */
    private RestHttpClientRuntimeHints() {
    }

    /**
     * Registra los hints de un cliente.
     *
     * @param hints el registro de hints de la aplicación
     * @param clientInterface la interfaz anotada con {@code @RestHttpClient}
     */
    public static void register(RuntimeHints hints, Class<?> clientInterface) {
        hints.proxies().registerJdkProxy(clientInterface);
        hints.reflection().registerType(clientInterface, MemberCategory.INVOKE_PUBLIC_METHODS);

        Set<Class<? extends Annotation>> annotations = new HashSet<>();
        collectAnnotations(clientInterface, annotations);
        List<Type> bound = new ArrayList<>();
        for (Method method : clientInterface.getMethods()) {
            collectAnnotations(method, annotations);
            bound.add(method.getGenericReturnType());
            for (Parameter parameter : method.getParameters()) {
                collectAnnotations(parameter, annotations);
                bound.add(parameter.getParameterizedType());
            }
        }
        // Las anotaciones anidadas de @RestHttpClient también se sintetizan al resolver los placeholders.
        annotations.addAll(List.of(RestHttpClient.class, SSLConfig.class, CircuitBreakerConfig.class,
                RetryConfig.class, RateLimiterConfig.class, TimeLimiterConfig.class, MethodConfig.class));
        annotations.forEach(type -> registerAnnotation(hints, type));

        RestHttpClient client = clientInterface.getAnnotation(RestHttpClient.class);
        if (client != null) {
            registerComponent(hints, client.errorHandler());
            for (Class<?> interceptor : client.interceptors()) {
                registerComponent(hints, interceptor);
            }
            if (client.fallback() != void.class) {
                registerComponent(hints, client.fallback());
            }
        }
        for (Type type : bound) {
            registerBinding(hints, type);
        }
    }

    /**
     * Reúne las anotaciones de un elemento y, de forma recursiva, sus metaanotaciones (por ejemplo
     * {@code @GetMapping} y su {@code @RequestMapping}).
     *
     * @param element el elemento anotado
     * @param found las anotaciones ya encontradas, a las que se añaden las nuevas
     */
    private static void collectAnnotations(AnnotatedElement element, Set<Class<? extends Annotation>> found) {
        for (Annotation annotation : element.getAnnotations()) {
            if (found.add(annotation.annotationType())) {
                collectAnnotations(annotation.annotationType(), found);
            }
        }
    }

    private static void registerAnnotation(RuntimeHints hints, Class<? extends Annotation> type) {
        hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_METHODS);
        // La copia sintetizada de las anotaciones de la librería es un proxy JDK de la anotación.
        if (type.getPackageName().startsWith("com.juniverse.client.")) {
            hints.proxies().registerJdkProxy(type);
        }
    }

    /**
     * Registra una clase que la librería instancia: constructores, campos y métodos, para que funcione también
     * con inyección de dependencias.
     *
     * @param hints el registro de hints
     * @param type la clase que se instancia
     */
    private static void registerComponent(RuntimeHints hints, Class<?> type) {
        hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS);
    }

    /**
     * Registra para Jackson los tipos propios de la aplicación; ignora los del JDK y de Spring.
     *
     * @param hints el registro de hints
     * @param type el tipo, posiblemente genérico
     */
    private static void registerBinding(RuntimeHints hints, Type type) {
        ResolvableType resolvable = ResolvableType.forType(type);
        Class<?> raw = resolvable.resolve(Object.class);
        if (!isFrameworkType(raw)) {
            BINDING.registerReflectionHints(hints.reflection(), raw);
        }
        for (ResolvableType generic : resolvable.getGenerics()) {
            registerBinding(hints, generic.getType());
        }
        if (raw.isArray()) {
            registerBinding(hints, raw.getComponentType());
        }
    }

    private static boolean isFrameworkType(Class<?> type) {
        if (type.isPrimitive() || type == Object.class) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("org.springframework.")
                || name.startsWith("[");
    }
}
