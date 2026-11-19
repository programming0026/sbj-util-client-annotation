package io.github.programming0026.juniverse.client.aot;

import io.github.programming0026.juniverse.client.util.Constants;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.CircuitBreakerConfig;
import io.github.programming0026.juniverse.client.autoconfigure.MethodConfig;
import io.github.programming0026.juniverse.client.autoconfigure.ProxyConfig;
import io.github.programming0026.juniverse.client.autoconfigure.RateLimiterConfig;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.autoconfigure.SSLConfig;
import io.github.programming0026.juniverse.client.autoconfigure.TimeLimiterConfig;
import io.github.programming0026.juniverse.client.config.JsonMapperConfigurer;
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
 * Registers the GraalVM hints (proxies and reflection) that a {@code @RestHttpClient} client needs.
 *
 * On the JVM the library discovers things by reflection: it creates a JDK proxy of the interface, reads its
 * methods, parameters and annotations, creates synthesized copies of the annotations (to resolve placeholders),
 * instantiates the error handler, the interceptors and the fallback, and Jackson binds the return and body
 * types. In a native image none of that exists unless it is declared beforehand.
 *
 * @author Java Universe
 */
public final class RestHttpClientRuntimeHints {

    /** Registers the types that Jackson binds. */
    private static final BindingReflectionHintsRegistrar BINDING = new BindingReflectionHintsRegistrar();

    /**
     * Utility class: it is not instantiated.
     */
    private RestHttpClientRuntimeHints() {
    }

    /**
     * Registers the hints of a client.
     *
     * @param hints the application's hints registry
     * @param clientInterface the interface annotated with {@code @RestHttpClient}
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
        annotations.addAll(List.of(RestHttpClient.class, SSLConfig.class, CircuitBreakerConfig.class,
                RetryConfig.class, RateLimiterConfig.class, TimeLimiterConfig.class, MethodConfig.class,
                ProxyConfig.class));
        annotations.forEach(type -> registerAnnotation(hints, type));

        RestHttpClient client = clientInterface.getAnnotation(RestHttpClient.class);
        if (client != null) {
            registerComponent(hints, client.errorHandler());
            for (Class<?> interceptor : client.interceptors()) {
                registerComponent(hints, interceptor);
            }
            if (client.jsonConfig() != JsonMapperConfigurer.class) {
                registerComponent(hints, client.jsonConfig());
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
     * Collects the annotations of an element and, recursively, their meta-annotations (for example
     * {@code @GetMapping} and its {@code @RequestMapping}).
     *
     * @param element the annotated element
     * @param found the annotations already found, to which the new ones are added
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
        if (type.getPackageName().startsWith(Constants.BASE + ".")) {
            hints.proxies().registerJdkProxy(type);
        }
    }

    /**
     * Registers a class that the library instantiates: constructors, fields and methods, so that it also works
     * with dependency injection.
     *
     * @param hints the hints registry
     * @param type the class that is instantiated
     */
    private static void registerComponent(RuntimeHints hints, Class<?> type) {
        hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS);
    }

    /**
     * Registers for Jackson the application's own types; it ignores the JDK and Spring ones.
     *
     * @param hints the hints registry
     * @param type the type, possibly generic
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
