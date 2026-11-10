package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.MethodConfig;
import io.github.programming0026.juniverse.client.autoconfigure.ProxyConfig;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redirecciones y proxy, contra servidores HTTP locales (uno hace de proxy y registra lo que recibe).
 *
 * @author Java Universe
 */
class RestHttpClientProxyRedirectTest {

    // ---- Redirecciones ----

    @RestHttpClient(url = "${t.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface FollowingClient {
        @GetMapping("/old")
        String old();

        @GetMapping("/hop/0")
        String loop();
    }

    @RestHttpClient(url = "${t.url}", followRedirects = false, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface NotFollowingClient {
        @GetMapping("/old")
        ResponseEntity<String> old();
    }

    @RestHttpClient(url = "${t.url}", maxRedirects = 3, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface MethodTimeoutClient {
        @GetMapping("/hop/0")
        @MethodConfig(readTimeout = 5000)
        String loop();
    }

    @RestHttpClient(url = "${t.url}", maxRedirects = 3, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface LimitedClient {
        @GetMapping("/hop/0")
        String loop();
    }

    @RestHttpClient(url = "${t.url}", maxRedirects = 0)
    interface BadMaxRedirectsClient {
        @GetMapping("/old")
        String old();
    }

    // ---- Proxy ----

    @RestHttpClient(url = "http://backend.test:8080", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            proxy = @ProxyConfig(host = "127.0.0.1", port = "${t.proxy.port}"))
    interface ProxiedClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "http://backend.test:8080", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            proxy = @ProxyConfig(host = "127.0.0.1", port = "${t.proxy.port}", username = "ana",
                    password = "secreto"))
    interface AuthenticatedProxyClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "${t.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            proxy = @ProxyConfig(host = "127.0.0.1", port = "${t.proxy.port}", nonProxyHosts = {"localhost",
                    "127.*"}))
    interface ExcludedHostClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "${t.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            proxy = @ProxyConfig(host = "127.0.0.1", port = "${t.proxy.port}"))
    interface NotExcludedClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "${t.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            proxy = @ProxyConfig(host = "${t.proxy.none:}", port = "${t.proxy.port}"))
    interface UnsetProxyClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "${t.url}", proxy = @ProxyConfig(host = "127.0.0.1", port = "70000"))
    interface BadPortClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "${t.url}", proxy = @ProxyConfig(host = "127.0.0.1", port = "abc"))
    interface TextPortClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "${t.url}", proxy = @ProxyConfig(host = "127.0.0.1", scheme = "socks"))
    interface BadSchemeClient {
        @GetMapping("/items")
        String items();
    }

    @RestHttpClient(url = "${t.url}", proxy = @ProxyConfig(host = "127.0.0.1", password = "x"))
    interface PasswordWithoutUserClient {
        @GetMapping("/items")
        String items();
    }

    private HttpServer target;

    private HttpServer proxy;

    private AnnotationConfigApplicationContext context;

    private final AtomicInteger loopHits = new AtomicInteger();

    /** URI absoluta y credenciales de cada petición que llegó al proxy. */
    private final List<String> proxyRequests = new CopyOnWriteArrayList<>();

    private final List<String> proxyAuthorizations = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        target = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        target.createContext("/old", exchange -> {
            exchange.getResponseHeaders().add("Location", "/new");
            reply(exchange, 302, "");
        });
        target.createContext("/new", exchange -> reply(exchange, 200, "final"));
        // Cadena infinita de URLs distintas (un bucle exacto lo corta antes Apache como redirección circular).
        target.createContext("/hop/", exchange -> {
            loopHits.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            int n = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
            exchange.getResponseHeaders().add("Location", "/hop/" + (n + 1));
            reply(exchange, 302, "");
        });
        target.createContext("/items", exchange -> reply(exchange, 200, "direct"));
        target.start();

        proxy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        proxy.createContext("/", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
            boolean wantsAuth = "ana".equals(System.getProperty("proxy.test.user"));
            if (wantsAuth && authorization == null) {
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"test\"");
                reply(exchange, 407, "");
                return;
            }
            proxyRequests.add(exchange.getRequestURI().toString());
            if (authorization != null) {
                proxyAuthorizations.add(authorization);
            }
            reply(exchange, 200, "via-proxy");
        });
        proxy.start();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("proxy.test.user");
        if (context != null) {
            context.close();
        }
        target.stop(0);
        proxy.stop(0);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private <T> T client(Class<T> type) throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "t.url", "http://127.0.0.1:" + target.getAddress().getPort(),
                "t.proxy.port", String.valueOf(proxy.getAddress().getPort()))));
        context.refresh();
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    // ---- Redirecciones ----

    @Test
    void redirectsAreFollowedByDefault() throws Exception {
        assertThat(client(FollowingClient.class).old()).isEqualTo("final");
    }

    @Test
    void withFollowRedirectsDisabledTheRedirectResponseIsReturned() throws Exception {
        ResponseEntity<String> response = client(NotFollowingClient.class).old();

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst("Location")).isEqualTo("/new");
    }

    @Test
    void aMethodReadTimeoutKeepsTheRedirectLimit() throws Exception {
        MethodTimeoutClient client = client(MethodTimeoutClient.class);

        assertThatThrownBy(client::loop).isInstanceOf(Exception.class);
        assertThat(loopHits.get()).isEqualTo(4);
    }

    @Test
    void aRedirectLoopStopsAtMaxRedirects() throws Exception {
        LimitedClient client = client(LimitedClient.class);

        assertThatThrownBy(client::loop).isInstanceOf(Exception.class);
        assertThat(loopHits.get()).isEqualTo(4);
    }

    @Test
    void maxRedirectsMustBePositive() {
        assertThatThrownBy(() -> client(BadMaxRedirectsClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("maxRedirects must be at least 1");
    }

    // ---- Proxy ----

    @Test
    void requestsGoThroughTheProxyInAbsoluteForm() throws Exception {
        assertThat(client(ProxiedClient.class).items()).isEqualTo("via-proxy");
        assertThat(proxyRequests).containsExactly("http://backend.test:8080/items");
    }

    @Test
    void theProxyCredentialsAnswerTheChallenge() throws Exception {
        System.setProperty("proxy.test.user", "ana");

        assertThat(client(AuthenticatedProxyClient.class).items()).isEqualTo("via-proxy");
        String expected = "Basic " + Base64.getEncoder().encodeToString("ana:secreto".getBytes(StandardCharsets.UTF_8));
        assertThat(proxyAuthorizations).containsExactly(expected);
    }

    @Test
    void nonProxyHostsReachTheTargetDirectly() throws Exception {
        assertThat(client(ExcludedHostClient.class).items()).isEqualTo("direct");
        assertThat(proxyRequests).isEmpty();
    }

    @Test
    void aHostNotExcludedGoesThroughTheProxy() throws Exception {
        assertThat(client(NotExcludedClient.class).items()).isEqualTo("via-proxy");
        assertThat(proxyRequests).hasSize(1);
    }

    @Test
    void anEmptyHostMeansNoProxy() throws Exception {
        assertThat(client(UnsetProxyClient.class).items()).isEqualTo("direct");
        assertThat(proxyRequests).isEmpty();
    }

    @Test
    void anInvalidPortIsRejected() {
        assertThatThrownBy(() -> client(BadPortClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("proxy port").hasMessageContaining("70000");
        assertThatThrownBy(() -> client(TextPortClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("'abc'");
    }

    @Test
    void anUnknownSchemeIsRejected() {
        assertThatThrownBy(() -> client(BadSchemeClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("http or https").hasMessageContaining("socks");
    }

    @Test
    void aPasswordWithoutUsernameIsRejected() {
        assertThatThrownBy(() -> client(PasswordWithoutUserClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("password but no username");
    }
}
