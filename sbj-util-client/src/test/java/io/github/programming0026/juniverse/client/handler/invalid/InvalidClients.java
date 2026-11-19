package io.github.programming0026.juniverse.client.handler.invalid;

import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Badly declared client that nobody injects: the context must still fail on startup.
 *
 * @author Java Universe
 */
public final class InvalidClients {

    private InvalidClients() {
    }

    /** Configuration that enables the clients of this package. */
    @Configuration
    @EnableRestHttpClients
    public static class Config {
    }

    /** Client with a path variable without {@code @PathVariable}. */
    @RestHttpClient(url = "http://localhost")
    public interface UnboundPathVariableClient {

        @GetMapping("/items/{id}")
        String item();
    }
}
