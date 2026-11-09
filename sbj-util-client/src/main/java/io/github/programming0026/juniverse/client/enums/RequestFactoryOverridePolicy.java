package io.github.programming0026.juniverse.client.enums;

import io.github.programming0026.juniverse.client.exception.ClientException;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.util.Locale;

/**
 * Qué hace la librería cuando un customizer ({@code RestClientCustomizer}, o los de un
 * {@code RestTemplateBuilder}) reemplaza la request factory de un cliente.
 *
 * La request factory de la librería es la que lleva el pool de conexiones, los timeouts y el SSL de
 * {@code @RestHttpClient}. Los customizers se aplican después de ella para que los tests puedan sustituirla
 * por un {@code MockRestServiceServer}, pero un customizer pensado para otros clientes de la aplicación
 * (por ejemplo, uno global que fija timeouts) también podría reemplazarla por accidente y dejar al cliente
 * sin su configuración. Esta política decide qué reemplazos se aceptan. Se configura con la propiedad
 * {@value #PROPERTY}.
 */
public enum RequestFactoryOverridePolicy {

    /** Por defecto: solo se aceptan los dobles de pruebas de Spring; cualquier otro reemplazo se ignora. */
    AUTO,

    /** Se acepta cualquier reemplazo (con un aviso si no es un doble de pruebas). */
    ALLOW,

    /** Nunca se acepta un reemplazo, ni siquiera el de un doble de pruebas. */
    DENY;

    /** Nombre de la propiedad que elige la política. */
    public static final String PROPERTY = "rest-http-client.request-factory-override";

    /** Cuántos envoltorios (por ejemplo {@code BufferingClientHttpRequestFactory}) se miran por dentro. */
    private static final int MAX_UNWRAP_DEPTH = 5;

    /** Paquete de los envoltorios de request factory de Spring que se pueden mirar por dentro. */
    private static final String SPRING_HTTP_CLIENT_PACKAGE = "org.springframework.http.client.";

    /**
     * Convierte el valor de la propiedad.
     *
     * @param value el valor ({@code auto}, {@code allow} o {@code deny}, sin distinguir mayúsculas); si es
     *              nulo o vacío se usa {@link #AUTO}
     * @return la política
     * @throws ClientException si el valor no es válido
     */
    public static RequestFactoryOverridePolicy from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ClientException(PROPERTY + " must be auto, allow or deny but was '" + value + "'", e);
        }
    }

    /**
     * Indica si la política acepta que un customizer deje esta request factory en el cliente.
     *
     * @param replacement la request factory con la que se reemplazaría la de la librería, o null si se
     *                    pide la de Spring por defecto
     * @return true si se acepta
     */
    public boolean accepts(ClientHttpRequestFactory replacement) {
        return switch (this) {
            case ALLOW -> true;
            case DENY -> false;
            default -> isTestDouble(replacement);
        };
    }

    /**
     * Indica si la request factory es un doble de pruebas de Spring: la que instala
     * {@code MockRestServiceServer} (y los {@code MockServerRestClientCustomizer} de Spring Boot), también
     * si va envuelta en otra de {@code org.springframework.http.client} (por ejemplo, con
     * {@code bufferContent()}), o un mock de Mockito.
     *
     * @param factory la request factory
     * @return true si es un doble de pruebas
     */
    public static boolean isTestDouble(ClientHttpRequestFactory factory) {
        ClientHttpRequestFactory current = factory;
        for (int depth = 0; current != null && depth < MAX_UNWRAP_DEPTH; depth++) {
            String name = current.getClass().getName();
            if (name.startsWith("org.springframework.test.") || name.startsWith("org.springframework.boot.test.")
                    || name.contains("$MockitoMock$")) {
                return true;
            }
            current = name.startsWith(SPRING_HTTP_CLIENT_PACKAGE) ? wrapped(current) : null;
        }
        return false;
    }

    /**
     * Obtiene la request factory que envuelve un envoltorio de Spring, con su método público
     * {@code getDelegate()}.
     *
     * @param wrapper el envoltorio
     * @return la request factory envuelta, o null si no es un envoltorio de Spring
     */
    private static ClientHttpRequestFactory wrapped(ClientHttpRequestFactory wrapper) {
        return wrapper instanceof AbstractClientHttpRequestFactoryWrapper spring ? spring.getDelegate() : null;
    }
}
