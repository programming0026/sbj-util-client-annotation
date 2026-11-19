package io.github.programming0026.juniverse.client.handler.placeholder;

import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client configured entirely with placeholders, for the integration test.
 *
 * @author Java Universe
 */
public final class PlaceholderClients {

    private PlaceholderClients() {
    }

    /** Configuration whose base package is also a placeholder. */
    @Configuration
    @EnableRestHttpClients(basePackages = "${test.clients-package}")
    public static class Config {
    }

    /** Client with name, URL, path, headers and default value taken from the environment. */
    @RestHttpClient(name = "${test.client-name}", url = "${test.base-url}",
            retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    public interface ItemsClient {

        @GetMapping(path = "${test.prefix}/items", headers = "X-Api-Key=${test.api-key}",
                produces = "${test.accept:application/json}")
        String list(@RequestParam(name = "page", defaultValue = "${test.default-page}") Integer page);
    }
}
