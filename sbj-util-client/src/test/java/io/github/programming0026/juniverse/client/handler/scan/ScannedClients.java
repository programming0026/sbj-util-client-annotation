package io.github.programming0026.juniverse.client.handler.scan;

import org.springframework.web.bind.annotation.GetMapping;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;

/** Contenedor de las interfaces que encuentra {@link ScanConfig}. */
public final class ScannedClients {

    private ScannedClients() {
    }

    /** Cliente con nombre de bean explícito. */
    @RestHttpClient(name = "namedClient", url = "http://localhost")
    public interface NamedClient {

        @GetMapping("/a")
        String a();
    }

    /** Cliente cuyo nombre de bean se deriva del nombre de la interfaz. */
    @RestHttpClient(url = "http://localhost")
    public interface DerivedNameClient {

        @GetMapping("/b")
        String b();
    }

    /** Sin anotación: el escaneo debe ignorarlo. */
    public interface NotAClient {
    }
}
