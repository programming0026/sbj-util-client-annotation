package com.juniverse.client.handler.scan;

import org.springframework.web.bind.annotation.GetMapping;
import com.juniverse.client.autoconfigure.RestHttpClient;

/** Holder for the interfaces discovered by {@link ScanConfig}. */
public final class ScannedClients {

    private ScannedClients() {
    }

    /** Top-level style client with an explicit bean name. */
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

    /** Not annotated: must be ignored by the scanner. */
    public interface NotAClient {
    }
}
