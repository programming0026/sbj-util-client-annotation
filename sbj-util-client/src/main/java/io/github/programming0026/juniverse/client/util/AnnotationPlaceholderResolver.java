package io.github.programming0026.juniverse.client.util;

import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Resolves placeholders in the text attributes of an annotation.
 * Annotations are immutable, so a synthesized copy is created in which every {@code String} or
 * {@code String[]} attribute (also inside nested annotations) goes through the resolver.
 * The other attributes are kept as they are.
 *
 * @author Java Universe
 */
public final class AnnotationPlaceholderResolver {

    /**
     * Instance.
     */
    private AnnotationPlaceholderResolver() {
    }

    /**
     * Returns a copy of the annotation with the text attributes resolved.
     *
     * @param annotation the original annotation
     * @param resolver the function that resolves each text value
     * @param <A> the annotation type
     * @return the annotation with the values resolved
     */
    public static <A extends Annotation> A resolve(A annotation, UnaryOperator<String> resolver) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        AnnotationUtils.getAnnotationAttributes(annotation, false, false)
                .forEach((name, value) -> attributes.put(name, resolveValue(value, resolver)));
        @SuppressWarnings("unchecked")
        Class<A> type = (Class<A>) annotation.annotationType();
        return AnnotationUtils.synthesizeAnnotation(attributes, type, null);
    }

    /**
     * Resolves an attribute value according to its type.
     *
     * @param value the attribute value
     * @param resolver the function that resolves each text value
     * @return the resolved value
     */
    private static Object resolveValue(Object value, UnaryOperator<String> resolver) {
        if (value instanceof String text) {
            return resolver.apply(text);
        }
        if (value instanceof Annotation nested) {
            return resolve(nested, resolver);
        }
        if (value instanceof String[] || value instanceof Annotation[]) {
            int length = Array.getLength(value);
            Object resolved = Array.newInstance(value.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(resolved, i, resolveValue(Array.get(value, i), resolver));
            }
            return resolved;
        }
        return value;
    }
}
