package io.github.programming0026.juniverse.client.handler.invalid;

import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Cliente mal declarado que nadie inyecta: el contexto igual debe fallar al arrancar.
 *
 * @author Java Universe
 */
public final class InvalidClients {

    private InvalidClients() {
    }

    /** Configuración que activa los clientes de este paquete. */
    @Configuration
    @EnableRestHttpClients
    public static class Config {
    }

    /** Cliente con una variable de ruta sin {@code @PathVariable}. */
    @RestHttpClient(url = "http://localhost")
    public interface UnboundPathVariableClient {

        @GetMapping("/items/{id}")
        String item();
    }
}
