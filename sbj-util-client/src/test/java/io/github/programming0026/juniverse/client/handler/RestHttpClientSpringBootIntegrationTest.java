package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.handler.boot.BootClients;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Los clientes usan la configuración HTTP de la aplicación Spring Boot: el Jackson configurado, los
 * {@link RestClientCustomizer} y el {@link RestTemplateBuilder}, sin perder sus propios timeouts.
 *
 * @author Java Universe
 */
class RestHttpClientSpringBootIntegrationTest {

    private HttpServer server;

    private final AtomicReference<String> receivedHeader = new AtomicReference<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class, HttpClientAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class, RestClientAutoConfiguration.class,
                    RestTemplateAutoConfiguration.class))
            .withUserConfiguration(BootClients.Config.class)
            .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE");

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            receivedHeader.set(exchange.getRequestHeaders().getFirst("X-App"));
            byte[] body = "{\"item_name\":\"keyboard\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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

    private String url() {
        return "test.url=http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void restClientUsesApplicationJacksonAndCustomizers() {
        runner.withPropertyValues(url(), "test.slow-url=http://127.0.0.1:1")
                .withBean(RestClientCustomizer.class, () -> builder -> builder.defaultHeader("X-App", "orders"))
                .run(context -> {
                    BootClients.Item item = context.getBean(BootClients.RestClientItems.class).item();

                    assertThat(item.itemName()).isEqualTo("keyboard");
                    assertThat(receivedHeader.get()).isEqualTo("orders");
                });
    }

    @Test
    void restTemplateUsesApplicationRestTemplateBuilder() {
        runner.withPropertyValues(url(), "test.slow-url=http://127.0.0.1:1")
                .withBean(RestTemplateBuilder.class, () -> new RestTemplateBuilder().defaultHeader("X-App", "billing"))
                .run(context -> {
                    BootClients.Item item = context.getBean(BootClients.RestTemplateItems.class).item();

                    assertThat(receivedHeader.get()).isEqualTo("billing");
                    assertThat(item).isNotNull();
                });
    }

    @Test
    void restTemplateUsesApplicationJackson() {
        runner.withPropertyValues(url(), "test.slow-url=http://127.0.0.1:1")
                .run(context -> assertThat(context.getBean(BootClients.RestTemplateItems.class).item().itemName())
                        .isEqualTo("keyboard"));
    }

    @Test
    void globalBuilderTimeoutsDoNotOverrideClientTimeouts() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            runner.withPropertyValues(url(), "test.slow-url=http://127.0.0.1:" + silent.getLocalPort())
                    .withBean(RestTemplateBuilder.class,
                            () -> new RestTemplateBuilder().readTimeout(Duration.ofSeconds(60)))
                    .run(context -> {
                        BootClients.SlowRestTemplateItems client = context.getBean(BootClients.SlowRestTemplateItems.class);

                        // El readTimeout del cliente (300 ms) se respeta aunque el builder global diga 60 s.
                        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                                assertThatThrownBy(client::item).isInstanceOf(ResourceAccessException.class));
                    });
        }
    }

    @Test
    void worksWithoutSpringBootAutoConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(BootClients.Config.class)
                .withPropertyValues(url(), "test.slow-url=http://127.0.0.1:1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // Sin el Jackson de Boot no hay snake_case: el campo queda sin mapear.
                    assertThat(context.getBean(BootClients.RestClientItems.class).item().itemName()).isNull();
                });
    }
}
