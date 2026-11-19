package io.github.programming0026.juniverse.client.handler.boot;

import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.enums.ClientType;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Clients to test the integration with the Spring Boot configuration.
 *
 * @author Java Universe
 */
public final class BootClients {

    private BootClients() {
    }

    /** Configuration that enables the clients of this package. */
    @Configuration
    @EnableRestHttpClients
    public static class Config {
    }

    /** Response with a camelCase field (in JSON it arrives in snake_case). */
    public record Item(String itemName) {
    }

    /** Client with RestClient. */
    @RestHttpClient(url = "${test.url}", retry = @RetryConfig(maxRetries = 1))
    public interface RestClientItems {

        @GetMapping("/item")
        Item item();
    }

    /** Client with RestTemplate. */
    @RestHttpClient(url = "${test.url}", type = ClientType.REST_TEMPLATE, retry = @RetryConfig(maxRetries = 1))
    public interface RestTemplateItems {

        @GetMapping("/item")
        Item item();
    }

    /** Client with RestTemplate and its own short read timeout. */
    @RestHttpClient(url = "${test.slow-url}", type = ClientType.REST_TEMPLATE, readTimeout = 300,
            retry = @RetryConfig(maxRetries = 1))
    public interface SlowRestTemplateItems {

        @GetMapping("/item")
        String item();
    }
}
