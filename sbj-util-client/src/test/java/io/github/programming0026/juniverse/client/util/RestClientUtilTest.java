package io.github.programming0026.juniverse.client.util;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.SSLConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import com.sun.net.httpserver.HttpServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Java Universe
 */
class RestClientUtilTest {

    @RestHttpClient(url = "http://localhost:8080")
    interface DefaultClient {
    }

    @RestHttpClient(url = "https://localhost:8443", connectionTimeToLive = 60000,
            ssl = @SSLConfig(keystorePath = SSLUtilTest.KEYSTORE, keystorePassword = SSLUtilTest.PASSWORD,
                    truststorePath = SSLUtilTest.KEYSTORE, truststorePassword = SSLUtilTest.PASSWORD))
    interface KeystoreClient {
    }

    @RestHttpClient(url = "https://localhost:8443", ssl = @SSLConfig(truststorePath = SSLUtilTest.KEYSTORE,
            truststorePassword = SSLUtilTest.PASSWORD))
    interface TruststoreOnlyClient {
    }

    @RestHttpClient(url = "https://localhost:8443", ssl = @SSLConfig(enabled = false))
    interface InsecureClient {
    }

    @RestHttpClient(url = "https://localhost:8443", ssl = @SSLConfig(keystorePath = "missing-keystore.jks",
            keystorePassword = "x"))
    interface BrokenClient {
    }

    /** Dirección no enrutable: la conexión TCP queda colgada hasta que vence el timeout de conexión. */
    @RestHttpClient(url = "http://10.255.255.1:81", connectTimeout = 300, readTimeout = 60000)
    interface UnreachableClient {
    }

    /** El servidor de prueba acepta la conexión pero nunca responde. */
    @RestHttpClient(url = "http://127.0.0.1", connectTimeout = 60000, readTimeout = 300)
    interface SlowServerClient {
    }

    private final RestClientUtil util = new RestClientUtil(new SSLUtil());

    private String previousProfile;

    @BeforeEach
    void saveProfile() {
        previousProfile = System.getProperty("spring.profiles.active");
    }

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) {
            System.clearProperty("spring.profiles.active");
        } else {
            System.setProperty("spring.profiles.active", previousProfile);
        }
    }

    private static RestHttpClient config(Class<?> type) {
        return type.getAnnotation(RestHttpClient.class);
    }

    @Test
    void connectTimeoutLimitsTcpConnection() {
        RestClient client = util.configureRestClient(config(UnreachableClient.class));

        // Sin el timeout de conexión, HttpClient espera hasta 3 minutos. Si la red rechaza la
        // dirección de inmediato, el test también pasa (falla rápido de todos modos).
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                assertThatThrownBy(() -> client.get().uri("/").retrieve().toBodilessEntity())
                        .isInstanceOf(ResourceAccessException.class));
    }

    @Test
    void httpClientDoesNotRetryOnItsOwn() throws Exception {
        AtomicInteger received = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            received.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            RestTemplate template = util.configureRestTemplate(config(DefaultClient.class));
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

            // Los reintentos son de Resilience4j: HttpClient no debe repetir por su cuenta un GET con 503.
            assertTimeoutPreemptively(Duration.ofMillis(900), () ->
                    assertThatThrownBy(() -> template.getForObject(url, String.class))
                            .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));
            assertThat(received).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readTimeoutLimitsWaitingForResponse() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            RestTemplate template = util.configureRestTemplate(config(SlowServerClient.class));
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/";

            assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                    assertThatThrownBy(() -> template.getForObject(url, String.class))
                            .isInstanceOf(ResourceAccessException.class)
                            .rootCause()
                            .isInstanceOf(SocketTimeoutException.class));
        }
    }

    @Test
    void configureRestClientWithDefaults() {
        RestClient client = util.configureRestClient(config(DefaultClient.class));

        assertThat(client).isNotNull();
    }

    @Test
    void configureRestTemplateUsesHttpComponents() {
        RestTemplate template = util.configureRestTemplate(config(DefaultClient.class));

        assertThat(template.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void configureWithKeystoreAndTimeToLive() {
        assertThat(util.configureRestClient(config(KeystoreClient.class))).isNotNull();
        assertThat(util.configureRestTemplate(config(KeystoreClient.class))).isNotNull();
    }

    @Test
    void configureWithTruststoreOnly() {
        assertThat(util.configureRestClient(config(TruststoreOnlyClient.class))).isNotNull();
    }

    @Test
    void configureInsecureInDevelopmentProfile() {
        assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null);
        System.setProperty("spring.profiles.active", "dev");

        assertThat(util.configureRestClient(config(InsecureClient.class))).isNotNull();
        assertThat(util.configureRestTemplate(config(InsecureClient.class))).isNotNull();
    }

    @Test
    void configureRestClientWrapsSslErrors() {
        assertThatThrownBy(() -> util.configureRestClient(config(BrokenClient.class)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Error configuring HTTP client");
    }

    @Test
    void configureRestTemplateWrapsSslErrors() {
        assertThatThrownBy(() -> util.configureRestTemplate(config(BrokenClient.class)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Error configuring HTTP client");
    }
}
