package io.github.programming0026.juniverse.client.util;

import java.util.OptionalLong;

/**
 * Lleva el {@code readTimeout} de un método ({@code @MethodConfig}) hasta la request factory de Apache
 * HttpClient, que lo lee al crear la petición. Funciona con {@code RestClient} y {@code RestTemplate} porque
 * la petición se crea en el mismo hilo que la inició.
 *
 * Es de uso interno de la librería: el handler lo fija justo antes de hacer la petición y lo limpia al terminar.
 */
public final class MethodReadTimeout {

    /** El timeout en milisegundos de la petición en curso del hilo, o null si se usa el del cliente. */
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private MethodReadTimeout() {
    }

    /**
     * Fija el read timeout de la petición que este hilo está por hacer.
     *
     * @param millis el timeout en milisegundos
     */
    public static void set(long millis) {
        CURRENT.set(millis);
    }

    /** Quita el read timeout del hilo, para que las siguientes peticiones usen el del cliente. */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Obtiene el read timeout fijado para este hilo.
     *
     * @return el timeout en milisegundos, o vacío si no hay ninguno
     */
    public static OptionalLong current() {
        Long value = CURRENT.get();
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }
}
