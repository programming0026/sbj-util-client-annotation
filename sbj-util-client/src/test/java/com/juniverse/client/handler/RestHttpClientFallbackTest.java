package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.CircuitBreakerConfig;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.autoconfigure.TimeLimiterConfig;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El {@code fallback} responde cuando el servicio no está disponible y solo entonces. */
class RestHttpClientFallbackTest {

    /** Respuestas del fallback, distinguibles de las del servidor. */
    public static class ItemsFallback implements ItemsClient {

        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public String find(String id) {
            CALLS.incrementAndGet();
            return "fallback:" + id;
        }

        @Override
        public String list(String filter) {
            CALLS.incrementAndGet();
            throw new IllegalStateException("the fallback itself failed");
        }
    }

    @RestHttpClient(url = "${fb.url}", readTimeout = 400, connectTimeout = 400,
            retry = @RetryConfig(maxRetries = 2, retryDelay = 1), fallback = ItemsFallback.class)
    interface ItemsClient {

        @GetMapping("/items/{id}")
        String find(@PathVariable("id") String id);

        @GetMapping("/items")
        String list(@RequestParam("filter") String filter);
    }

    @RestHttpClient(url = "${fb.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            circuitBreaker = @CircuitBreakerConfig(enabled = true, slidingWindowSize = 2, minimumNumberOfCalls = 2,
                    waitDurationInOpenState = 60_000),
            fallback = BreakerFallback.class)
    interface BreakerClient {

        @GetMapping("/items/{id}")
        String find(@PathVariable("id") String id);
    }

    /** Fallback del cliente con circuit breaker. */
    public static class BreakerFallback implements BreakerClient {

        @Override
        public String find(String id) {
            return "open:" + id;
        }
    }

    @RestHttpClient(url = "${fb.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 150), fallback = SlowFallback.class)
    interface SlowClient {

        @GetMapping("/slow")
        String slow();
    }

    /** Fallback del cliente con time limiter. */
    public static class SlowFallback implements SlowClient {

        @Override
        public String slow() {
            return "too-slow";
        }
    }

    @RestHttpClient(url = "${fb.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            fallback = String.class)
    interface WrongFallbackClient {

        @GetMapping("/items")
        String list();
    }

    /** Clase abstracta: no se puede instanciar. */
    abstract static class AbstractFallback implements AbstractFallbackClient {
    }

    @RestHttpClient(url = "${fb.url}", fallback = AbstractFallback.class)
    interface AbstractFallbackClient {

        @GetMapping("/items")
        String list();
    }

    @RestHttpClient(url = "${fb.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface PlainClient {

        @GetMapping("/items/{id}")
        String find(@PathVariable("id") String id);
    }

    private HttpServer server;

    private ExecutorService serverThreads;

    private AnnotationConfigApplicationContext context;

    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        ItemsFallback.CALLS.set(0);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverThreads = Executors.newFixedThreadPool(4);
        server.setExecutor(serverThreads);
        server.createContext("/items/ok", exchange -> reply(exchange, 200, "real"));
        server.createContext("/items/missing", exchange -> reply(exchange, 404, "nope"));
        server.createContext("/items/down", exchange -> {
            hits.incrementAndGet();
            reply(exchange, 503, "down");
        });
        server.createContext("/items/teapot", exchange -> reply(exchange, 418, "teapot"));
        server.createContext("/slow", exchange -> {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, "late");
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        server.stop(0);
        serverThreads.shutdownNow();
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private <T> T client(Class<T> type, int port) throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("fb.url", "http://127.0.0.1:" + port)));
        context.refresh();
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    private <T> T client(Class<T> type) throws Exception {
        return client(type, server.getAddress().getPort());
    }

    @Test
    void aSuccessfulCallDoesNotUseTheFallback() throws Exception {
        assertThat(client(ItemsClient.class).find("ok")).isEqualTo("real");
        assertThat(ItemsFallback.CALLS).hasValue(0);
    }

    @Test
    void aServerErrorUsesTheFallbackAfterTheRetries() throws Exception {
        assertThat(client(ItemsClient.class).find("down")).isEqualTo("fallback:down");

        assertThat(hits).hasValue(2);
        assertThat(ItemsFallback.CALLS).hasValue(1);
    }

    @Test
    void clientErrorsAreNotCoveredByTheFallback() throws Exception {
        ItemsClient client = client(ItemsClient.class);

        assertThatThrownBy(() -> client.find("missing")).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> client.find("teapot")).isInstanceOf(HttpClientErrorException.class);
        assertThat(ItemsFallback.CALLS).hasValue(0);
    }

    @Test
    void aNetworkFailureUsesTheFallback() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        assertThat(client(ItemsClient.class, closedPort).find("any")).isEqualTo("fallback:any");
    }

    @Test
    void anOpenCircuitUsesTheFallback() throws Exception {
        BreakerClient client = client(BreakerClient.class);

        // Dos fallas llenan la ventana y abren el circuito; a partir de ahí el fallback responde sin llamar.
        assertThat(client.find("down")).isEqualTo("open:down");
        assertThat(client.find("down")).isEqualTo("open:down");
        int hitsWhenOpened = hits.get();
        assertThat(client.find("down")).isEqualTo("open:down");

        assertThat(hits).hasValue(hitsWhenOpened);
    }

    @Test
    void aTimeoutUsesTheFallback() throws Exception {
        assertThat(client(SlowClient.class).slow()).isEqualTo("too-slow");
    }

    @Test
    void usageErrorsAreNotCoveredByTheFallback() throws Exception {
        ItemsClient client = client(ItemsClient.class);

        assertThatThrownBy(() -> client.list(null)).isInstanceOf(ClientException.class)
                .hasMessageContaining("request parameter");
        assertThat(ItemsFallback.CALLS).hasValue(0);
    }

    @Test
    void anExceptionFromTheFallbackReachesTheCaller() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        ItemsClient client = client(ItemsClient.class, closedPort);

        assertThatThrownBy(() -> client.list("x")).isInstanceOf(IllegalStateException.class)
                .hasMessage("the fallback itself failed");
    }

    @Test
    void withoutFallbackTheErrorIsPropagated() throws Exception {
        assertThatThrownBy(() -> client(PlainClient.class).find("down"))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }

    @Test
    void theFallbackMustImplementTheClientInterface() {
        assertThatThrownBy(() -> client(WrongFallbackClient.class)).isInstanceOf(ClientException.class)
                .hasMessage("Fallback java.lang.String of client WrongFallbackClient must be a concrete class that "
                        + "implements " + WrongFallbackClient.class.getName());
        assertThatThrownBy(() -> client(AbstractFallbackClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("must be a concrete class");
    }

    @Test
    void unavailabilityIsClassifiedByCause() {
        assertThat(RestHttpClientInvocationHandler.isUnavailability(new IllegalArgumentException())).isFalse();
        assertThat(RestHttpClientInvocationHandler.isUnavailability(new ClientException("config"))).isFalse();
        assertThat(RestHttpClientInvocationHandler.isUnavailability(
                new ClientException("timeout", new TimeoutException()))).isTrue();
        assertThat(RestHttpClientInvocationHandler.isUnavailability(
                new ResourceAccessException("I/O error", new IOException()))).isTrue();
    }
}
