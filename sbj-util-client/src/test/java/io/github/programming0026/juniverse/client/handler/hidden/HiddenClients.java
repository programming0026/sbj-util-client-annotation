package io.github.programming0026.juniverse.client.handler.hidden;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Client with a non-public interface in another package: the library cannot call its fallback by reflection.
 *
 * @author Java Universe
 */
public final class HiddenClients {

    private HiddenClients() {
    }

    /** Package-private interface: its methods are not accessible from the library. */
    @RestHttpClient(url = "http://127.0.0.1:1", connectTimeout = 300, retry = @RetryConfig(maxRetries = 1,
            retryDelay = 1), fallback = HiddenFallback.class)
    interface Hidden {
        @GetMapping("/ping")
        String ping();
    }

    /** Public fallback of the non-public interface. */
    public static class HiddenFallback implements Hidden {
        @Override
        public String ping() {
            return "fallback";
        }
    }

    /** Client whose fallback is a non-public class. */
    @RestHttpClient(url = "http://127.0.0.1:1", connectTimeout = 300, retry = @RetryConfig(maxRetries = 1,
            retryDelay = 1), fallback = PackageFallback.class)
    public interface Open {
        @GetMapping("/ping")
        String ping();
    }

    /** Non-public fallback: the library can create it but not invoke its methods. */
    static class PackageFallback implements Open {
        @Override
        public String ping() {
            return "fallback";
        }
    }

    /**
     * Returns the non-public interface.
     *
     * @return the interface
     */
    public static Class<?> type() {
        return Hidden.class;
    }

    /**
     * Calls {@code ping()} on the client.
     *
     * @param client the client's proxy
     * @return the result
     */
    public static String ping(Object client) {
        return ((Hidden) client).ping();
    }
}
