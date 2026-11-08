package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.config.RestHttpClientCustomizer;
import com.juniverse.client.enums.ClientType;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link RestHttpClientCustomizer}: configuración de un cliente concreto, identificado por su nombre. */
class RestHttpClientCustomizerTest {

    @RestHttpClient(url = "${cz.url}", retry = @RetryConfig(maxRetries = 1))
    interface AlphaClient {

        @GetMapping("/who")
        String who();
    }

    @RestHttpClient(url = "${cz.url}", name = "beta", retry = @RetryConfig(maxRetries = 1))
    interface BetaClient {

        @GetMapping("/who")
        String who();
    }

    @RestHttpClient(url = "${cz.url}", type = ClientType.REST_TEMPLATE, retry = @RetryConfig(maxRetries = 1))
    interface TemplateClient {

        @GetMapping("/who")
        String who();
    }

    private HttpServer server;

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/who", exchange -> {
            byte[] body = String.valueOf(exchange.getRequestHeaders().getFirst("X-Client"))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        server.stop(0);
    }

    private void refresh(RestHttpClientCustomizer... customizers) {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("cz.url", "http://127.0.0.1:" + server.getAddress().getPort())));
        int index = 0;
        for (RestHttpClientCustomizer customizer : customizers) {
            context.registerBean("customizer" + index++, RestHttpClientCustomizer.class, () -> customizer);
        }
        context.refresh();
    }

    private <T> T client(Class<T> type) throws Exception {
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    /** Agrega un header con el nombre del cliente, para saber a quién se aplicó. */
    private static final class NameHeaderCustomizer implements RestHttpClientCustomizer {

        @Override
        public void customize(String clientName, RestClient.Builder builder) {
            builder.defaultHeader("X-Client", clientName);
        }

        @Override
        public void customize(String clientName, RestTemplate template) {
            template.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("X-Client", clientName);
                return execution.execute(request, body);
            });
        }
    }

    @Test
    void theCustomizerReceivesTheNameOfEachClient() throws Exception {
        refresh(new NameHeaderCustomizer());

        assertThat(client(AlphaClient.class).who()).isEqualTo("AlphaClient");
        assertThat(client(BetaClient.class).who()).isEqualTo("beta");
    }

    @Test
    void itAppliesToRestTemplateClientsToo() throws Exception {
        refresh(new NameHeaderCustomizer());

        assertThat(client(TemplateClient.class).who()).isEqualTo("TemplateClient");
    }

    @Test
    void aCustomizerCanTargetASingleClient() throws Exception {
        refresh(new RestHttpClientCustomizer() {
            @Override
            public void customize(String clientName, RestClient.Builder builder) {
                if ("beta".equals(clientName)) {
                    builder.defaultHeader("X-Client", "special");
                }
            }
        });

        assertThat(client(AlphaClient.class).who()).isEqualTo("null");
        assertThat(client(BetaClient.class).who()).isEqualTo("special");
    }

    @Test
    void customizersAreAppliedInOrder() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        @Order(2)
        class Second implements RestHttpClientCustomizer {
            @Override
            public void customize(String clientName, RestClient.Builder builder) {
                calls.add("second");
            }
        }
        @Order(1)
        class First implements RestHttpClientCustomizer {
            @Override
            public void customize(String clientName, RestClient.Builder builder) {
                calls.add("first");
            }
        }
        refresh(new Second(), new First());

        client(AlphaClient.class);

        assertThat(new ArrayList<>(calls)).containsExactly("first", "second");
    }

    @Test
    void aCustomizerCannotDropTheClientRequestFactoryByAccident() throws Exception {
        refresh(new RestHttpClientCustomizer() {
            @Override
            public void customize(String clientName, RestClient.Builder builder) {
                builder.requestFactory(new SimpleClientHttpRequestFactory() {
                    @Override
                    public org.springframework.http.client.ClientHttpRequest createRequest(
                            java.net.URI uri, org.springframework.http.HttpMethod httpMethod) {
                        throw new IllegalStateException("the replacement must not be used");
                    }
                });
            }

            @Override
            public void customize(String clientName, RestTemplate template) {
                template.setRequestFactory((uri, httpMethod) -> {
                    throw new IllegalStateException("the replacement must not be used");
                });
            }
        });

        assertThat(client(AlphaClient.class).who()).isEqualTo("null");
        assertThat(client(TemplateClient.class).who()).isEqualTo("null");
    }
}
