package io.github.programming0026.juniverse.client.handler.duplicate;

import io.github.programming0026.juniverse.client.util.Constants;
import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;

/**
 * Two clients with the same bean name, to provoke a registration error.
 *
 * @author Java Universe
 */
public final class DuplicateClients {

    private DuplicateClients() {
    }

    /** Configuration that explicitly scans this package. */
    @EnableRestHttpClients(basePackages = Constants.BASE + ".handler.duplicate")
    public static class Config {
    }

    /** First client. */
    @RestHttpClient(name = "dup", url = "http://localhost")
    public interface First {
    }

    /** Second client with the same name. */
    @RestHttpClient(name = "dup", url = "http://localhost")
    public interface Second {
    }
}
