package com.juniverse.client.handler.duplicate;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.autoconfigure.RestHttpClient;

/** Two clients sharing the same bean name, to trigger a registration error. */
public final class DuplicateClients {

    private DuplicateClients() {
    }

    /** Config that scans this package explicitly. */
    @EnableRestHttpClients(basePackages = "com.juniverse.client.handler.duplicate")
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
