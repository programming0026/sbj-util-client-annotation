package io.github.programming0026.juniverse.client.handler.hidden;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import org.springframework.web.bind.annotation.GetMapping;

/** Cliente con interfaz no pública en otro paquete: la librería no puede llamar a su fallback por reflexión. */
public final class HiddenClients {

    private HiddenClients() {
    }

    /** Interfaz de paquete: sus métodos no son accesibles desde la librería. */
    @RestHttpClient(url = "http://127.0.0.1:1", connectTimeout = 300, retry = @RetryConfig(maxRetries = 1,
            retryDelay = 1), fallback = HiddenFallback.class)
    interface Hidden {
        @GetMapping("/ping")
        String ping();
    }

    /** Fallback público de la interfaz no pública. */
    public static class HiddenFallback implements Hidden {
        @Override
        public String ping() {
            return "fallback";
        }
    }

    /** Cliente cuyo fallback es una clase no pública. */
    @RestHttpClient(url = "http://127.0.0.1:1", connectTimeout = 300, retry = @RetryConfig(maxRetries = 1,
            retryDelay = 1), fallback = PackageFallback.class)
    public interface Open {
        @GetMapping("/ping")
        String ping();
    }

    /** Fallback no público: la librería puede crearlo pero no invocar sus métodos. */
    static class PackageFallback implements Open {
        @Override
        public String ping() {
            return "fallback";
        }
    }

    /**
     * Devuelve la interfaz no pública.
     *
     * @return la interfaz
     */
    public static Class<?> type() {
        return Hidden.class;
    }

    /**
     * Llama a {@code ping()} del cliente.
     *
     * @param client el proxy del cliente
     * @return el resultado
     */
    public static String ping(Object client) {
        return ((Hidden) client).ping();
    }
}
