package com.juniverse.client.handler.placeholder;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Cliente configurado completamente con placeholders, para el test de integración. */
public final class PlaceholderClients {

    private PlaceholderClients() {
    }

    /** Configuración cuyo paquete base también es un placeholder. */
    @Configuration
    @EnableRestHttpClients(basePackages = "${test.clients-package}")
    public static class Config {
    }

    /** Cliente con nombre, URL, ruta, headers y valor por defecto tomados del entorno. */
    @RestHttpClient(name = "${test.client-name}", url = "${test.base-url}",
            retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    public interface ItemsClient {

        @GetMapping(path = "${test.prefix}/items", headers = "X-Api-Key=${test.api-key}",
                produces = "${test.accept:application/json}")
        String list(@RequestParam(name = "page", defaultValue = "${test.default-page}") Integer page);
    }
}
