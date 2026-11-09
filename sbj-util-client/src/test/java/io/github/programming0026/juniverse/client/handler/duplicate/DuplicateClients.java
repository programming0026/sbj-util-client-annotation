package io.github.programming0026.juniverse.client.handler.duplicate;

import io.github.programming0026.juniverse.client.ClientPackages;
import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;

/** Dos clientes con el mismo nombre de bean, para provocar un error de registro. */
public final class DuplicateClients {

    private DuplicateClients() {
    }

    /** Configuración que escanea este paquete de forma explícita. */
    @EnableRestHttpClients(basePackages = ClientPackages.BASE + ".handler.duplicate")
    public static class Config {
    }

    /** Primer cliente. */
    @RestHttpClient(name = "dup", url = "http://localhost")
    public interface First {
    }

    /** Segundo cliente con el mismo nombre. */
    @RestHttpClient(name = "dup", url = "http://localhost")
    public interface Second {
    }
}
