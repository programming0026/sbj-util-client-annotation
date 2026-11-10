package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
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

/**
 * Descargas con retorno {@code InputStream}: el cuerpo se lee progresivamente, la conexión se libera al cerrar el
 * flujo y los errores siguen siendo excepciones. El pool es de una sola conexión a propósito: cualquier fuga de
 * conexión haría fallar la siguiente llamada.
 *
 * @author Java Universe
 */
class RestHttpClientStreamingTest {

    @RestHttpClient(url = "${stream.url}", readTimeout = 300, connectTimeout = 400, maxConnections = 1,
            maxConnectionsPerRoute = 1, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface DownloadClient {

        @GetMapping("/download")
        InputStream download();

        @GetMapping("/small")
        ResponseEntity<InputStream> small();

        @GetMapping("/missing")
        InputStream missing();

        @GetMapping("/empty")
        InputStream empty();

        @GetMapping("/stall")
        InputStream stall();

        @PostMapping("/report")
        InputStream report(@RequestBody String request);
    }

    @RestHttpClient(url = "${stream.url}", readTimeout = 300, connectTimeout = 400, maxConnections = 1,
            maxConnectionsPerRoute = 1, retry = @RetryConfig(maxRetries = 3, retryDelay = 1))
    interface RetryingDownloadClient {

        @GetMapping("/unavailable")
        InputStream unavailable();

        @GetMapping("/small")
        InputStream small();
    }

    private static final byte[] SMALL_BODY = "PDFDATA".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;

    private ExecutorService serverThreads;

    private CountDownLatch release;

    private final AtomicInteger unavailableHits = new AtomicInteger();

    private AnnotationConfigApplicationContext context;

    private byte[] first;

    private byte[] rest;

    @BeforeEach
    void setUp() throws Exception {
        first = pattern(8192, 1);
        rest = pattern(40_000, 2);
        release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverThreads = Executors.newFixedThreadPool(4);
        server.setExecutor(serverThreads);

        server.createContext("/download", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, first.length + rest.length);
            OutputStream out = exchange.getResponseBody();
            out.write(first);
            out.flush();
            // Retiene el resto hasta que el cliente confirme que ya recibió el primer bloque.
            awaitRelease(5);
            out.write(rest);
            exchange.close();
        });
        server.createContext("/small", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"report.pdf\"");
            reply(exchange, 200, SMALL_BODY);
        });
        server.createContext("/missing", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            reply(exchange, 404, "{\"error\":\"nope\"}".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/unavailable", exchange -> {
            unavailableHits.incrementAndGet();
            reply(exchange, 503, "down".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/empty", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            OutputStream out = exchange.getResponseBody();
            out.write(pattern(10, 3));
            out.flush();
            awaitRelease(3);
            exchange.close();
        });
        server.createContext("/report", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            reply(exchange, 200, ("REPORT:" + request).getBytes(StandardCharsets.UTF_8));
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

    /** Espera la señal del test sin propagar la interrupción (los handlers de HttpServer solo lanzan IOException). */
    private void awaitRelease(int seconds) {
        try {
            release.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void reply(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static byte[] pattern(int size, int seed) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i * 31 + seed);
        }
        return bytes;
    }

    private <T> T client(Class<T> type) throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("stream.url", "http://127.0.0.1:" + server.getAddress().getPort())));
        context.refresh();
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    @Test
    void theBodyIsStreamedNotBufferedInMemory() throws Exception {
        DownloadClient client = client(DownloadClient.class);

        // Si la librería acumulara el cuerpo, esta llamada se bloquearía hasta que el servidor suelte el resto
        // (5 s); con streaming vuelve en cuanto llegan los headers y el primer bloque.
        InputStream in = assertTimeoutPreemptively(Duration.ofSeconds(3), client::download);

        try (in) {
            assertThat(in.readNBytes(first.length)).isEqualTo(first);
            release.countDown();
            assertThat(in.readAllBytes()).isEqualTo(rest);
        }
    }

    @Test
    void theBodyCanBeReadInBlocksWithAnOffset() throws Exception {
        try (InputStream in = client(DownloadClient.class).small().getBody()) {
            byte[] buffer = new byte[SMALL_BODY.length + 2];
            int first = in.read(buffer, 1, 3);
            int second = in.read(buffer, 1 + first, SMALL_BODY.length - first);

            assertThat(first).isEqualTo(3);
            assertThat(java.util.Arrays.copyOfRange(buffer, 1, 1 + first + second)).isEqualTo(SMALL_BODY);
            assertThat(buffer[0]).isZero();
            assertThat(in.read(buffer, 0, buffer.length)).isEqualTo(-1);
        }
    }

    @Test
    void aResponseEntityExposesStatusAndHeadersAndTheBody() throws Exception {
        ResponseEntity<InputStream> response = client(DownloadClient.class).small();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("report.pdf");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(SMALL_BODY.length);
        try (InputStream in = response.getBody()) {
            assertThat(in.readAllBytes()).isEqualTo(SMALL_BODY);
        }
    }

    @Test
    void thePostBodyIsSentAndTheAnswerCanBeStreamed() throws Exception {
        try (InputStream in = client(DownloadClient.class).report("monthly")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("REPORT:monthly");
        }
    }

    @Test
    void anOpenStreamKeepsItsConnectionUntilItIsClosed() throws Exception {
        DownloadClient client = client(DownloadClient.class);
        InputStream openStream = client.small().getBody();

        // El pool tiene una sola conexión y sigue ocupada: la segunda llamada espera 400 ms y falla.
        assertThatThrownBy(client::small).isInstanceOf(ResourceAccessException.class);

        openStream.close();
        openStream.close();
        try (InputStream in = client.small().getBody()) {
            assertThat(in.readAllBytes()).isEqualTo(SMALL_BODY);
        }
    }

    @Test
    void readingTheWholeBodyReleasesTheConnectionEvenWithoutClosing() throws Exception {
        DownloadClient client = client(DownloadClient.class);

        for (int i = 0; i < 3; i++) {
            assertThat(client.small().getBody().readAllBytes()).isEqualTo(SMALL_BODY);
        }
    }

    @Test
    void errorStatusesAreThrownAsExceptionsAndDoNotLeakTheConnection() throws Exception {
        DownloadClient client = client(DownloadClient.class);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(client::missing)
                    .isInstanceOf(HttpClientErrorException.NotFound.class)
                    .satisfies(e -> assertThat(((HttpClientErrorException) e).getResponseBodyAsString())
                            .contains("nope"));
        }
        try (InputStream in = client.small().getBody()) {
            assertThat(in.readAllBytes()).isEqualTo(SMALL_BODY);
        }
    }

    @Test
    void transientErrorsAreRetriedBeforeAnyStreamIsReturned() throws Exception {
        RetryingDownloadClient client = client(RetryingDownloadClient.class);

        assertThatThrownBy(client::unavailable).isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);

        assertThat(unavailableHits).hasValue(3);
        try (InputStream in = client.small()) {
            assertThat(in.readAllBytes()).isEqualTo(SMALL_BODY);
        }
    }

    @Test
    void anEmptyResponseGivesAnEmptyStream() throws Exception {
        try (InputStream in = client(DownloadClient.class).empty()) {
            assertThat(in).isNotNull();
            assertThat(in.readAllBytes()).isEmpty();
        }
    }

    @Test
    void aStalledBodyFailsWithTheReadTimeoutInsteadOfHanging() throws Exception {
        DownloadClient client = client(DownloadClient.class);
        InputStream in = client.stall();

        assertThat(in.readNBytes(10)).hasSize(10);
        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                assertThatThrownBy(in::read).isInstanceOf(SocketTimeoutException.class));
        in.close();

        // La conexión rota se descartó del pool: el cliente sigue funcionando.
        try (InputStream next = client.small().getBody()) {
            assertThat(next.readAllBytes()).isEqualTo(SMALL_BODY);
        }
    }
}
