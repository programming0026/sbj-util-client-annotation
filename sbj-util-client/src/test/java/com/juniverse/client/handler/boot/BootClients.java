package com.juniverse.client.handler.boot;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.enums.ClientType;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

/** Clientes para probar la integración con la configuración de Spring Boot. */
public final class BootClients {

    private BootClients() {
    }

    /** Configuración que activa los clientes de este paquete. */
    @Configuration
    @EnableRestHttpClients
    public static class Config {
    }

    /** Respuesta con un campo en camelCase (en JSON llega en snake_case). */
    public record Item(String itemName) {
    }

    /** Cliente con RestClient. */
    @RestHttpClient(url = "${test.url}", retry = @RetryConfig(maxRetries = 1))
    public interface RestClientItems {

        @GetMapping("/item")
        Item item();
    }

    /** Cliente con RestTemplate. */
    @RestHttpClient(url = "${test.url}", type = ClientType.REST_TEMPLATE, retry = @RetryConfig(maxRetries = 1))
    public interface RestTemplateItems {

        @GetMapping("/item")
        Item item();
    }

    /** Cliente con RestTemplate y un timeout de lectura corto propio. */
    @RestHttpClient(url = "${test.slow-url}", type = ClientType.REST_TEMPLATE, readTimeout = 300,
            retry = @RetryConfig(maxRetries = 1))
    public interface SlowRestTemplateItems {

        @GetMapping("/item")
        String item();
    }
}
