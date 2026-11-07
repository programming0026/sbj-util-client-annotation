package com.juniverse.client.handler.duplicate;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.autoconfigure.RestHttpClient;

/** Dos clientes con el mismo nombre de bean, para provocar un error de registro. */
public final class DuplicateClients {

    private DuplicateClients() {
    }

    /** Configuración que escanea este paquete de forma explícita. */
    @EnableRestHttpClients(basePackages = "com.juniverse.client.handler.duplicate")
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
