package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.enums.ClientType;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interceptores por cliente, con inyección de dependencias y después de los globales de la aplicación.
 *
 * @author Java Universe
 */
class RestHttpClientInterceptorTest {

    /** Bean de la aplicación que usa el interceptor de autenticación. */
    static class TokenProvider {

        String token() {
            return "secret-token";
        }
    }

    /** Interceptor sin constructor vacío: Spring lo crea inyectando {@link TokenProvider}. */
    static class AuthInterceptor implements ClientHttpRequestInterceptor {

        private final TokenProvider tokenProvider;

        AuthInterceptor(TokenProvider tokenProvider) {
            this.tokenProvider = tokenProvider;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            request.getHeaders().setBearerAuth(tokenProvider.token());
            request.getHeaders().add("X-Chain", "auth");
            return execution.execute(request, body);
        }
    }

    /** Segundo interceptor, para comprobar el orden. */
    static class TraceInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            request.getHeaders().add("X-Chain", "trace");
            return execution.execute(request, body);
        }
    }

    @RestHttpClient(url = "${test.url}", retry = @RetryConfig(maxRetries = 1),
            interceptors = {AuthInterceptor.class, TraceInterceptor.class})
    interface RestClientWithInterceptors {

        @GetMapping("/ping")
        String ping();
    }

    @RestHttpClient(url = "${test.url}", type = ClientType.REST_TEMPLATE, retry = @RetryConfig(maxRetries = 1),
            interceptors = {AuthInterceptor.class, TraceInterceptor.class})
    interface RestTemplateWithInterceptors {

        @GetMapping("/ping")
        String ping();
    }

    /** Interceptor global de la aplicación, que debe ejecutarse antes que los del cliente. */
    private static final ClientHttpRequestInterceptor GLOBAL = (request, body, execution) -> {
        request.getHeaders().add("X-Chain", "global");
        return execution.execute(request, body);
    };

    private HttpServer server;

    private final AtomicReference<List<String>> chain = new AtomicReference<>();

    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            chain.set(exchange.getRequestHeaders().get("X-Chain"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
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

    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("test.url", "http://127.0.0.1:" + server.getAddress().getPort())));
        context.registerBean(TokenProvider.class);
        return context;
    }

    private static <T> T client(Class<T> type, AnnotationConfigApplicationContext context) throws Exception {
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    @Test
    void restClientAppliesGlobalThenClientInterceptorsInOrder() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            context.registerBean(RestClientCustomizer.class, () -> builder -> builder.requestInterceptor(GLOBAL));
            context.refresh();

            assertThat(client(RestClientWithInterceptors.class, context).ping()).isEqualTo("pong");

            assertThat(chain.get()).containsExactly("global", "auth", "trace");
            assertThat(authorization.get()).isEqualTo("Bearer secret-token");
        }
    }

    @Test
    void restTemplateAppliesGlobalThenClientInterceptorsInOrder() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            context.registerBean(RestTemplateBuilder.class, () -> new RestTemplateBuilder().interceptors(GLOBAL));
            context.refresh();

            assertThat(client(RestTemplateWithInterceptors.class, context).ping()).isEqualTo("pong");

            assertThat(chain.get()).containsExactly("global", "auth", "trace");
            assertThat(authorization.get()).isEqualTo("Bearer secret-token");
        }
    }

    @Test
    void existingInterceptorBeanIsReused() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            TraceInterceptor bean = new TraceInterceptor();
            context.registerBean(TraceInterceptor.class, () -> bean);
            context.refresh();

            assertThat(client(RestClientWithInterceptors.class, context).ping()).isEqualTo("pong");

            assertThat(chain.get()).containsExactly("auth", "trace");
        }
    }
}
