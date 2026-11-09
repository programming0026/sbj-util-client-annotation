package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.MethodConfig;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.autoconfigure.TimeLimiterConfig;
import io.github.programming0026.juniverse.client.enums.ClientType;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.MethodReadTimeout;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** {@code @MethodConfig}: ajustes de un solo método sin tocar los del resto del cliente. */
class RestHttpClientMethodConfigTest {

    @RestHttpClient(url = "${mc.url}", readTimeout = 250, connectTimeout = 500,
            retry = @RetryConfig(maxRetries = 3, retryDelay = 1))
    interface TimeoutClient {

        @GetMapping("/delayed")
        String delayedWithClientTimeout();

        @GetMapping("/delayed")
        @MethodConfig(readTimeout = 3000)
        String delayedWithLongerTimeout();

        @GetMapping("/slowbody")
        InputStream slowBodyWithClientTimeout();

        @GetMapping("/slowbody")
        @MethodConfig(readTimeout = 3000)
        InputStream slowBodyWithLongerTimeout();
    }

    @RestHttpClient(url = "${mc.url}", readTimeout = 5000, connectTimeout = 500,
            retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface ShortTimeoutClient {

        @GetMapping("/hang")
        String hangWithClientTimeout();

        @GetMapping("/hang")
        @MethodConfig(readTimeout = 200)
        String hangWithShorterTimeout();

        @GetMapping("/stall")
        @MethodConfig(readTimeout = 300)
        InputStream stallWithShorterTimeout();
    }

    @RestHttpClient(url = "${mc.url}", readTimeout = 250, connectTimeout = 500, type = ClientType.REST_TEMPLATE,
            retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface TemplateClient {

        @GetMapping("/delayed")
        @MethodConfig(readTimeout = 3000)
        String delayed();
    }

    @RestHttpClient(url = "${mc.url}", retry = @RetryConfig(maxRetries = 3, retryDelay = 1))
    interface RetriesClient {

        @GetMapping("/down/inherited")
        String inherited();

        @GetMapping("/down/single")
        @MethodConfig(maxRetries = 1)
        String single();

        @GetMapping("/down/many")
        @MethodConfig(maxRetries = 5)
        String many();

        @GetMapping("/down/overloaded/{id}")
        @MethodConfig(maxRetries = 2)
        String overloaded(@PathVariable("id") String id);

        @GetMapping("/down/overloaded/{id}")
        @MethodConfig(maxRetries = 4)
        String overloaded(@PathVariable("id") int id);
    }

    @RestHttpClient(url = "${mc.url}", readTimeout = 5000, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface TimeLimiterClient {

        @GetMapping("/delayed")
        String withoutLimit();

        @GetMapping("/delayed")
        @MethodConfig(timeLimiterTimeout = 100)
        String withOwnLimit();
    }

    @RestHttpClient(url = "${mc.url}", readTimeout = 5000, retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 100))
    interface LimitedClient {

        @GetMapping("/delayed")
        String inherited();

        @GetMapping("/delayed")
        @MethodConfig(timeLimiterTimeout = 0)
        String noLimit();

        @GetMapping("/delayed")
        @MethodConfig(timeLimiterTimeout = 3000)
        String longerLimit();
    }

    @RestHttpClient(url = "${mc.url}")
    interface ZeroReadTimeoutClient {

        @GetMapping("/x")
        @MethodConfig(readTimeout = 0)
        String x();
    }

    @RestHttpClient(url = "${mc.url}")
    interface ZeroRetriesClient {

        @GetMapping("/x")
        @MethodConfig(maxRetries = 0)
        String x();
    }

    @RestHttpClient(url = "${mc.url}")
    interface NegativeLimiterClient {

        @GetMapping("/x")
        @MethodConfig(timeLimiterTimeout = -2)
        String x();
    }

    private HttpServer server;

    private ExecutorService serverThreads;

    private CountDownLatch release;

    private AnnotationConfigApplicationContext context;

    private final Map<String, AtomicInteger> hits = Map.of("inherited", new AtomicInteger(),
            "single", new AtomicInteger(), "many", new AtomicInteger(), "overloaded", new AtomicInteger());

    @BeforeEach
    void setUp() throws Exception {
        release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverThreads = Executors.newFixedThreadPool(8);
        server.setExecutor(serverThreads);
        server.createContext("/delayed", exchange -> {
            sleep(700);
            reply(exchange, 200, "late");
        });
        server.createContext("/hang", exchange -> {
            sleep(2500);
            reply(exchange, 200, "never");
        });
        server.createContext("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            OutputStream out = exchange.getResponseBody();
            out.write(new byte[10]);
            out.flush();
            awaitRelease();
            exchange.close();
        });
        server.createContext("/slowbody", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            OutputStream out = exchange.getResponseBody();
            out.write(new byte[10]);
            out.flush();
            sleep(600);
            out.write(new byte[90]);
            exchange.close();
        });
        server.createContext("/down", exchange -> {
            String path = exchange.getRequestURI().getPath();
            hits.get(path.split("/")[2]).incrementAndGet();
            reply(exchange, 503, "down");
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        if (context != null) {
            context.close();
        }
        server.stop(0);
        serverThreads.shutdownNow();
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitRelease() {
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
                Map.of("mc.url", "http://127.0.0.1:" + server.getAddress().getPort())));
        context.refresh();
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    @Test
    void aMethodCanWaitLongerThanTheClientDefault() throws Exception {
        TimeoutClient client = client(TimeoutClient.class);

        assertThatThrownBy(client::delayedWithClientTimeout).isInstanceOf(ResourceAccessException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
        assertThat(client.delayedWithLongerTimeout()).isEqualTo("late");
    }

    @Test
    void aMethodCanWaitLessThanTheClientDefault() throws Exception {
        ShortTimeoutClient client = client(ShortTimeoutClient.class);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(client::hangWithShorterTimeout).isInstanceOf(ResourceAccessException.class)
                        .hasRootCauseInstanceOf(SocketTimeoutException.class));
    }

    @Test
    void theMethodTimeoutDoesNotLeakIntoLaterCallsOfTheSameThread() throws Exception {
        TimeoutClient client = client(TimeoutClient.class);

        assertThat(client.delayedWithLongerTimeout()).isEqualTo("late");

        assertThat(MethodReadTimeout.current()).isEmpty();
        assertThatThrownBy(client::delayedWithClientTimeout).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void theMethodTimeoutAlsoRulesEachReadOfAStreamedBody() throws Exception {
        ShortTimeoutClient client = client(ShortTimeoutClient.class);
        InputStream in = client.stallWithShorterTimeout();

        assertThat(in.readNBytes(10)).hasSize(10);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(in::read).isInstanceOf(SocketTimeoutException.class));
        in.close();
    }

    @Test
    void aLongerMethodTimeoutAlsoAppliesToTheStreamedBody() throws Exception {
        TimeoutClient client = client(TimeoutClient.class);

        // El cuerpo se detiene 600 ms a la mitad: con el timeout del cliente (250 ms) la lectura falla...
        try (InputStream in = client.slowBodyWithClientTimeout()) {
            assertThatThrownBy(in::readAllBytes).isInstanceOf(SocketTimeoutException.class);
        }
        // ...y con el del método (3 s) se completa.
        try (InputStream in = client.slowBodyWithLongerTimeout()) {
            assertThat(in.readAllBytes()).hasSize(100);
        }
    }

    @Test
    void theMethodTimeoutWorksWithRestTemplateToo() throws Exception {
        assertThat(client(TemplateClient.class).delayed()).isEqualTo("late");
    }

    @Test
    void aMethodCanHaveItsOwnNumberOfAttempts() throws Exception {
        RetriesClient client = client(RetriesClient.class);

        assertThatThrownBy(client::inherited).isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        assertThatThrownBy(client::single).isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        assertThatThrownBy(client::many).isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);

        assertThat(hits.get("inherited")).hasValue(3);
        assertThat(hits.get("single")).hasValue(1);
        assertThat(hits.get("many")).hasValue(5);
    }

    @Test
    void overloadedMethodsKeepTheirOwnSettings() throws Exception {
        RetriesClient client = client(RetriesClient.class);

        assertThatThrownBy(() -> client.overloaded("a")).isInstanceOf(HttpServerErrorException.class);
        assertThat(hits.get("overloaded")).hasValue(2);

        hits.get("overloaded").set(0);
        assertThatThrownBy(() -> client.overloaded(7)).isInstanceOf(HttpServerErrorException.class);
        assertThat(hits.get("overloaded")).hasValue(4);
    }

    @Test
    void aMethodCanHaveATimeLimitEvenIfTheClientHasNone() throws Exception {
        TimeLimiterClient client = client(TimeLimiterClient.class);

        assertThat(client.withoutLimit()).isEqualTo("late");
        assertThatThrownBy(client::withOwnLimit).isInstanceOf(ClientException.class)
                .hasMessage("Request timed out after 100 ms");
    }

    @Test
    void aMethodCanRemoveOrExtendTheClientTimeLimit() throws Exception {
        LimitedClient client = client(LimitedClient.class);

        assertThatThrownBy(client::inherited).isInstanceOf(ClientException.class)
                .hasMessage("Request timed out after 100 ms");
        assertThat(client.noLimit()).isEqualTo("late");
        assertThat(client.longerLimit()).isEqualTo("late");
    }

    @Test
    void invalidSettingsAreRejectedWhenTheClientIsCreated() {
        assertThatThrownBy(() -> client(ZeroReadTimeoutClient.class)).isInstanceOf(ClientException.class)
                .hasMessage("Method ZeroReadTimeoutClient.x declares @MethodConfig.readTimeout = 0, but it must be "
                        + "greater than 0, or -1 to inherit");
        assertThatThrownBy(() -> client(ZeroRetriesClient.class)).isInstanceOf(ClientException.class)
                .hasMessage("Method ZeroRetriesClient.x declares @MethodConfig.maxRetries = 0, but it must be at "
                        + "least 1, or -1 to inherit");
        assertThatThrownBy(() -> client(NegativeLimiterClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("@MethodConfig.timeLimiterTimeout = -2");
    }
}
