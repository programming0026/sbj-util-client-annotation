package com.juniverse.client.util;

import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Resuelve placeholders en los atributos de texto de una anotación.
 * Las anotaciones son inmutables, así que se crea una copia sintetizada en la que cada atributo
 * {@code String} o {@code String[]} (también dentro de anotaciones anidadas) pasa por el resolvedor.
 * Los demás atributos se conservan tal cual.
 */
public final class AnnotationPlaceholderResolver {

    /**
     * Instancia.
     */
    private AnnotationPlaceholderResolver() {
    }

    /**
     * Devuelve una copia de la anotación con los atributos de texto resueltos.
     *
     * @param annotation la anotación original
     * @param resolver la función que resuelve cada valor de texto
     * @param <A> el tipo de la anotación
     * @return la anotación con los valores resueltos
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
     * Resuelve un valor de atributo según su tipo.
     *
     * @param value el valor del atributo
     * @param resolver la función que resuelve cada valor de texto
     * @return el valor resuelto
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
