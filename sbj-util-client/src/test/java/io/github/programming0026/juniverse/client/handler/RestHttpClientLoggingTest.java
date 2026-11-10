package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.util.Constants;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.RequestLoggingInterceptor;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code logging = true}: una línea por petición, sin headers, cuerpos ni valores de la query.
 *
 * @author Java Universe
 */
class RestHttpClientLoggingTest {

    private static final String LOGGER = Constants.BASE + ".http";

    @RestHttpClient(name = "payments", url = "${t.url}", logging = true,
            retry = @RetryConfig(maxRetries = 3, retryDelay = 1))
    interface LoggedClient {
        @GetMapping("/items")
        String items(@RequestParam("token") String token, @RequestHeader("Authorization") String auth);

        @GetMapping("/down")
        String down();
    }

    @RestHttpClient(name = "quiet", url = "${t.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface QuietClient {
        @GetMapping("/items")
        String items(@RequestParam("token") String token);
    }

    @RestHttpClient(url = "${t.url}", logging = true, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface UnnamedClient {
        @GetMapping("/items")
        String items();
    }

    private HttpServer server;

    private AnnotationConfigApplicationContext context;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/items", exchange -> reply(exchange, 200, "ok"));
        server.createContext("/down", exchange -> reply(exchange, 503, "down"));
        server.start();
        logs = new ListAppender<>();
        logs.start();
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LOGGER);
        logger.addAppender(logs);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LOGGER)).detachAppender(logs);
        if (context != null) {
            context.close();
        }
        server.stop(0);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private <T> T client(Class<T> type) throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("t.url", "http://127.0.0.1:" + server.getAddress().getPort())));
        context.refresh();
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    private List<ILoggingEvent> events() {
        return List.copyOf(logs.list);
    }

    @Test
    void logsMethodUrlStatusAndDurationAtInfo() throws Exception {
        client(LoggedClient.class).items("s3cr3t", "Bearer abc");

        assertThat(events()).hasSize(1);
        ILoggingEvent event = events().get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .matches("GET http://127\\.0\\.0\\.1:\\d+/items\\?token -> 200 \\(\\d+ ms\\)");
    }

    @Test
    void usesOneLoggerPerClientNamedAfterTheClient() throws Exception {
        client(LoggedClient.class).items("x", "y");

        assertThat(events().get(0).getLoggerName()).isEqualTo(RequestLoggingInterceptor.LOGGER_PREFIX + "payments");
    }

    @Test
    void anUnnamedClientUsesTheInterfaceSimpleName() throws Exception {
        client(UnnamedClient.class).items();

        assertThat(events().get(0).getLoggerName())
                .isEqualTo(RequestLoggingInterceptor.LOGGER_PREFIX + "UnnamedClient");
    }

    @Test
    void neverLogsQueryValuesOrHeaders() throws Exception {
        client(LoggedClient.class).items("s3cr3t", "Bearer abc");

        assertThat(events()).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain("s3cr3t").doesNotContain("Bearer").doesNotContain("Authorization"));
    }

    @Test
    void logsEveryAttemptOfARetriedRequest() throws Exception {
        LoggedClient client = client(LoggedClient.class);

        assertThatThrownBy(client::down).isInstanceOf(Exception.class);

        assertThat(events()).hasSizeGreaterThan(1)
                .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("/down -> 503"));
    }

    @Test
    void aConnectionFailureIsLoggedAtWarn() throws Exception {
        LoggedClient client = client(LoggedClient.class);
        server.stop(0);

        assertThatThrownBy(client::down).isInstanceOf(Exception.class);

        assertThat(events()).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("GET").contains("/down failed after");
        });
    }

    @Test
    void doesNothingWithoutTheFlag() throws Exception {
        client(QuietClient.class).items("x");

        assertThat(events()).isEmpty();
    }

    @Test
    void doesNotLogBelowTheConfiguredLevel() throws Exception {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LOGGER)).setLevel(Level.ERROR);

        client(LoggedClient.class).items("x", "y");

        assertThat(events()).isEmpty();
    }

    @Test
    void describeKeepsOnlyTheNamesOfTheQuery() {
        assertThat(RequestLoggingInterceptor.describe(URI.create("https://user:pw@api.test:8443/a/b?x=1&y=2&z&")))
                .isEqualTo("https://api.test:8443/a/b?x&y&z");
        assertThat(RequestLoggingInterceptor.describe(URI.create("http://api.test/a"))).isEqualTo("http://api.test/a");
        assertThat(RequestLoggingInterceptor.describe(URI.create("http://api.test/a?")))
                .isEqualTo("http://api.test/a");
        assertThat(RequestLoggingInterceptor.describe(URI.create("/relative?k=v"))).isEqualTo("/relative?k");
    }
}
