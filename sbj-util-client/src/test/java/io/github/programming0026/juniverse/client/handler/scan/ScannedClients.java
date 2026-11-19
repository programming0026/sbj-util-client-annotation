package io.github.programming0026.juniverse.client.handler.scan;

import org.springframework.web.bind.annotation.GetMapping;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;

/**
 * Container of the interfaces that {@link ScanConfig} finds.
 *
 * @author Java Universe
 */
public final class ScannedClients {

    private ScannedClients() {
    }

    /** Client with an explicit bean name. */
    @RestHttpClient(name = "namedClient", url = "http://localhost")
    public interface NamedClient {

        @GetMapping("/a")
        String a();
    }

    /** Client whose bean name is derived from the interface name. */
    @RestHttpClient(url = "http://localhost")
    public interface DerivedNameClient {

        @GetMapping("/b")
        String b();
    }

    /** Without annotation: the scan must ignore it. */
    public interface NotAClient {
    }
}
