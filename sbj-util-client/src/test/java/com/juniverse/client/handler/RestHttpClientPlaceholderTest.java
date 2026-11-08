package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.SSLConfig;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.handler.placeholder.PlaceholderClients;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Resolución de placeholders {@code ${...}} con el Environment de Spring, de punta a punta. */
class RestHttpClientPlaceholderTest {

    @RestHttpClient(url = "${missing.base-url}")
    interface MissingPropertyClient {
    }

    @RestHttpClient(url = "${test.base-url:http://localhost}",
            ssl = @SSLConfig(keystorePath = "${test.keystore}", keystorePassword = "${test.keystore-password}"))
    interface SslPlaceholderClient {
    }

    private HttpServer server;

    private final AtomicReference<String> receivedUri = new AtomicReference<>();

    private final AtomicReference<String> receivedApiKey = new AtomicReference<>();

    private final AtomicReference<String> receivedAccept = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            receivedUri.set(exchange.getRequestURI().toString());
            receivedApiKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            receivedAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void resolvesPlaceholdersFromEnvironmentEndToEnd() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("test.clients-package", "com.juniverse.client.handler.placeholder");
        properties.put("test.client-name", "itemsFromConfig");
        properties.put("test.base-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        properties.put("test.prefix", "/v2");
        properties.put("test.api-key", "secret-123");
        properties.put("test.default-page", "7");

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            context.register(PlaceholderClients.Config.class);
            context.refresh();

            PlaceholderClients.ItemsClient client =
                    context.getBean("itemsFromConfig", PlaceholderClients.ItemsClient.class);

            assertThat(client.list(null)).isEqualTo("ok");
        }

        assertThat(receivedUri.get()).isEqualTo("/api/v2/items?page=7");
        assertThat(receivedApiKey.get()).isEqualTo("secret-123");
        assertThat(receivedAccept.get()).isEqualTo("application/json");
    }

    @Test
    void sslSettingsAcceptPlaceholders() throws Exception {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "test.keystore", "classpath:test-keystore.p12",
                "test.keystore-password", "changeit")));
        RestHttpClientFactoryBean<SslPlaceholderClient> factory = factory(SslPlaceholderClient.class);
        factory.setApplicationContext(context);

        assertThat(factory.getObject()).isInstanceOf(SslPlaceholderClient.class);
    }

    @Test
    void unresolvablePlaceholderFailsWhenCreatingClient() {
        RestHttpClientFactoryBean<MissingPropertyClient> factory = factory(MissingPropertyClient.class);
        factory.setApplicationContext(new GenericApplicationContext());

        assertThatThrownBy(factory::getObject)
                .isInstanceOf(ClientException.class)
                .hasMessage("Cannot resolve placeholder in '${missing.base-url}' for client MissingPropertyClient")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unresolvableBeanNameFailsRegistration() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "test.clients-package", "com.juniverse.client.handler.placeholder")));
            context.register(PlaceholderClients.Config.class);

            assertThatThrownBy(context::refresh)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause()
                    .hasMessageContaining("test.client-name");
        }
    }

    private static <T> RestHttpClientFactoryBean<T> factory(Class<T> type) {
        return new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(), new RestClientUtil(new SSLUtil()));
    }
}
