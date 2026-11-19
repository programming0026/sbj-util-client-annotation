package io.github.programming0026.juniverse.client.testing;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.config.RestHttpClientCustomizer;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * To test applications with <b>several</b> clients of the library: it creates a {@link MockRestServiceServer}
 * for each client and hands them out by name. With Spring Boot's {@code MockServerRestClientCustomizer},
 * on the other hand, {@code getServer()} fails as soon as there is more than one client and {@code getServers()}
 * does not say which client each server belongs to.
 *
 * It is declared as a bean (a method that returns {@code new MockRestHttpClients()}) in the test's
 * configuration and works with {@code REST_CLIENT} and {@code REST_TEMPLATE} clients. Then, in each test:
 *
 * <pre>{@code
 * mocks.server(UsersClient.class).expect(requestTo("https://users/1")).andRespond(...);
 * mocks.server(PaymentsClient.class).expect(requestTo("https://payments/charge")).andRespond(...);
 * // ... run the code under test ...
 * mocks.verify();
 * }</pre>
 *
 * It is not part of the library's jar (so as not to depend on {@code spring-test} in production): it lives in the
 * library's tests and is copied to those of your application; the README shows it in full.
 *
 * @author Java Universe
 */
public class MockRestHttpClients implements RestHttpClientCustomizer {

    /** The servers by client name. */
    private final Map<String, MockRestServiceServer> servers = new ConcurrentHashMap<>();

    /** Whether the servers keep the response body so it can be read more than once. */
    private boolean bufferContent;

    /** Whether the servers accept requests in any order. */
    private boolean ignoreExpectOrder;

    /**
     * Makes the servers keep the body of requests and responses in memory so it can be read
     * more than once (for example, with interceptors that log it). It must be called before the
     * clients are created.
     *
     * @return this object
     */
    public MockRestHttpClients bufferContent() {
        this.bufferContent = true;
        return this;
    }

    /**
     * Makes the servers accept the expected requests in any order. It must be called before the
     * clients are created.
     *
     * @return this object
     */
    public MockRestHttpClients ignoreExpectOrder() {
        this.ignoreExpectOrder = true;
        return this;
    }

    /**
     * Binds a server to the builder of a {@code REST_CLIENT} client.
     *
     * @param clientName the client name
     * @param builder the client's builder
     */
    @Override
    public void customize(String clientName, RestClient.Builder builder) {
        register(clientName, MockRestServiceServer.bindTo(builder));
    }

    /**
     * Binds a server to the RestTemplate of a {@code REST_TEMPLATE} client.
     *
     * @param clientName the client name
     * @param template the client's RestTemplate
     */
    @Override
    public void customize(String clientName, RestTemplate template) {
        register(clientName, MockRestServiceServer.bindTo(template));
    }

    private void register(String clientName, MockRestServiceServer.MockRestServiceServerBuilder builder) {
        if (bufferContent) {
            builder.bufferContent();
        }
        builder.ignoreExpectOrder(ignoreExpectOrder);
        servers.put(clientName, builder.build());
    }

    /**
     * Gets the server of a client.
     *
     * @param clientName the client name: the {@code name()} of {@code @RestHttpClient} or, if empty, the
     *                   simple name of the interface
     * @return the server, to declare the expected requests
     * @throws IllegalStateException if there is no client with that name (the message lists the existing ones)
     */
    public MockRestServiceServer server(String clientName) {
        MockRestServiceServer server = servers.get(clientName);
        if (server == null) {
            throw new IllegalStateException("There is no mocked client named '" + clientName + "'. Known clients: "
                    + clientNames());
        }
        return server;
    }

    /**
     * Gets the server of the client declared by an interface. It uses the {@code name()} of {@code @RestHttpClient}
     * exactly as written or, if empty, the simple name of the interface; with a {@code name()} that carries
     * placeholders it uses {@link #server(String)} with the already resolved name.
     *
     * @param clientInterface the client interface
     * @return the server, to declare the expected requests
     * @throws IllegalStateException if that client does not exist
     */
    public MockRestServiceServer server(Class<?> clientInterface) {
        RestHttpClient annotation = clientInterface.getAnnotation(RestHttpClient.class);
        String name = annotation == null || annotation.name().isEmpty()
                ? clientInterface.getSimpleName()
                : annotation.name();
        return server(name);
    }

    /**
     * Gets the names of the clients with a server, sorted.
     *
     * @return the names
     */
    public Set<String> clientNames() {
        return new TreeSet<>(servers.keySet());
    }

    /**
     * Checks that all the clients made the expected requests. If several are missing, the error names
     * each client.
     *
     * @throws AssertionError if any client did not meet its expectations
     */
    public void verify() {
        Map<String, AssertionError> failures = new TreeMap<>();
        servers.forEach((name, server) -> {
            try {
                server.verify();
            } catch (AssertionError e) {
                failures.put(name, e);
            }
        });
        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder("Unmet expectations in " + failures.size() + " client(s):");
            failures.forEach((name, error) -> message.append("\n  [").append(name).append("] ")
                    .append(error.getMessage()));
            throw new AssertionError(message.toString());
        }
    }

    /** Clears the expectations and the recorded requests of all the clients, to reuse the context. */
    public void reset() {
        servers.values().forEach(MockRestServiceServer::reset);
    }
}
