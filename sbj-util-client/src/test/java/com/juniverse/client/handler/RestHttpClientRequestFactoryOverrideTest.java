package com.juniverse.client.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.juniverse.client.enums.RequestFactoryOverridePolicy;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.enums.ClientType;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.boot.restclient.test.MockServerRestClientCustomizer;
import org.springframework.boot.restclient.test.MockServerRestTemplateCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Qué pasa cuando un customizer reemplaza la request factory de un cliente: los mocks de Spring funcionan sin
 * configurar nada, y un reemplazo accidental no deja al cliente sin su pool, timeouts y SSL.
 */
class RestHttpClientRequestFactoryOverrideTest {

    private static final String MOCK_URL = "http://mock.invalid";

    @RestHttpClient(url = "${override.url}", retry = @RetryConfig(maxRetries = 1))
    interface PingClient {

        @GetMapping("/ping")
        String ping();
    }

    @RestHttpClient(url = "${override.url}", type = ClientType.REST_TEMPLATE, retry = @RetryConfig(maxRetries = 1))
    interface TemplatePingClient {

        @GetMapping("/ping")
        String ping();
    }

    /** Request factory que no debería usarse nunca: cuenta cuántas veces la llamaron. */
    static final class AccidentalFactory implements ClientHttpRequestFactory {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            calls.incrementAndGet();
            throw new IOException("the accidental request factory was used");
        }
    }

    /** Un customizer global pensado para otros clientes de la aplicación, que fija su propia request factory. */
    static final class GlobalTimeoutCustomizer implements RestClientCustomizer {

        private final AccidentalFactory factory;

        GlobalTimeoutCustomizer(AccidentalFactory factory) {
            this.factory = factory;
        }

        @Override
        public void customize(RestClient.Builder builder) {
            // Encadenado a propósito: las llamadas de antes y de después también deben llegar al builder real.
            builder.defaultHeader("X-Before", "1").requestFactory(factory).defaultHeader("X-After", "2");
        }
    }

    private HttpServer server;

    private final AtomicInteger hits = new AtomicInteger();

    private final AtomicReference<Map<String, String>> receivedHeaders = new AtomicReference<>(Map.of());

    private final List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            receivedHeaders.set(headers);
            byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        logs = new ListAppender<>();
        logs.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RequestFactoryGuard.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RequestFactoryGuard.class)).detachAppender(logs);
        contexts.forEach(AnnotationConfigApplicationContext::close);
        server.stop(0);
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private <T> T client(Class<T> type, String url, String policy, Consumer<AnnotationConfigApplicationContext> beans)
            throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("override.url", url);
        if (policy != null) {
            properties.put(RequestFactoryOverridePolicy.PROPERTY, policy);
        }
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        beans.accept(context);
        context.refresh();
        contexts.add(context);
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    private List<String> warnings() {
        return logs.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ------------------------------------------------------------------ mocks (lo que debe seguir funcionando)

    @Test
    void aMockRestServiceServerReplacesTheRequestFactoryOfARestClient() throws Exception {
        AtomicReference<MockRestServiceServer> mock = new AtomicReference<>();

        PingClient client = client(PingClient.class, MOCK_URL, null, context -> context.registerBean(
                RestClientCustomizer.class, () -> builder -> mock.set(MockRestServiceServer.bindTo(builder).build())));
        mock.get().expect(requestTo(MOCK_URL + "/ping")).andRespond(withSuccess("mocked", MediaType.TEXT_PLAIN));

        assertThat(client.ping()).isEqualTo("mocked");
        mock.get().verify();
        assertThat(hits).hasValue(0);
        assertThat(warnings()).isEmpty();
    }

    @Test
    void aBufferedMockIsRecognizedEvenThoughItIsWrapped() throws Exception {
        AtomicReference<MockRestServiceServer> mock = new AtomicReference<>();

        PingClient client = client(PingClient.class, MOCK_URL, null, context -> context.registerBean(
                RestClientCustomizer.class,
                () -> builder -> mock.set(MockRestServiceServer.bindTo(builder).bufferContent().build())));
        mock.get().expect(requestTo(MOCK_URL + "/ping")).andRespond(withSuccess("buffered", MediaType.TEXT_PLAIN));

        assertThat(client.ping()).isEqualTo("buffered");
        assertThat(warnings()).isEmpty();
    }

    @Test
    void springBootsMockServerRestClientCustomizerWorks() throws Exception {
        MockServerRestClientCustomizer bootMock = new MockServerRestClientCustomizer();

        PingClient client = client(PingClient.class, MOCK_URL, null,
                context -> context.registerBean(MockServerRestClientCustomizer.class, () -> bootMock));
        bootMock.getServer().expect(requestTo(MOCK_URL + "/ping"))
                .andRespond(withSuccess("boot mock", MediaType.TEXT_PLAIN));

        assertThat(client.ping()).isEqualTo("boot mock");
        assertThat(hits).hasValue(0);
    }

    @Test
    void aMockRestServiceServerReplacesTheRequestFactoryOfARestTemplate() throws Exception {
        AtomicReference<MockRestServiceServer> mock = new AtomicReference<>();
        RestTemplateCustomizer bind = template -> mock.set(MockRestServiceServer.bindTo(template).build());

        TemplatePingClient client = client(TemplatePingClient.class, MOCK_URL, null, context -> context.registerBean(
                RestTemplateBuilder.class, () -> new RestTemplateBuilder().additionalCustomizers(bind)));
        mock.get().expect(requestTo(MOCK_URL + "/ping")).andRespond(withSuccess("mocked", MediaType.TEXT_PLAIN));

        assertThat(client.ping()).isEqualTo("mocked");
        assertThat(hits).hasValue(0);
        assertThat(warnings()).isEmpty();
    }

    @Test
    void springBootsMockServerRestTemplateCustomizerWorks() throws Exception {
        MockServerRestTemplateCustomizer bootMock = new MockServerRestTemplateCustomizer();

        TemplatePingClient client = client(TemplatePingClient.class, MOCK_URL, null, context -> context.registerBean(
                RestTemplateBuilder.class, () -> new RestTemplateBuilder().additionalCustomizers(bootMock)));
        bootMock.getServer().expect(requestTo(MOCK_URL + "/ping"))
                .andRespond(withSuccess("boot mock", MediaType.TEXT_PLAIN));

        assertThat(client.ping()).isEqualTo("boot mock");
    }

    // ------------------------------------------------------------------ el riesgo: un reemplazo accidental

    @Test
    void anAccidentalRequestFactoryIsIgnoredAndTheClientKeepsItsOwn() throws Exception {
        AccidentalFactory accidental = new AccidentalFactory();

        PingClient client = client(PingClient.class, serverUrl(), null, context -> context.registerBean(
                RestClientCustomizer.class, () -> new GlobalTimeoutCustomizer(accidental)));

        assertThat(client.ping()).isEqualTo("pong");
        assertThat(accidental.calls).hasValue(0);
        assertThat(hits).hasValue(1);
        // El resto de lo que hizo el customizer (antes y después del reemplazo) sí se aplicó.
        assertThat(receivedHeaders.get()).containsEntry("x-before", "1").containsEntry("x-after", "2");
        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("Ignored the request factory " + AccidentalFactory.class.getName())
                .contains("RestClientCustomizer " + GlobalTimeoutCustomizer.class.getName())
                .contains("client PingClient")
                .contains("rest-http-client.request-factory-override=allow")
                .contains("currently 'auto'"));
    }

    @Test
    void anAccidentalRequestFactoryOnARestTemplateIsRestoredAndInterceptorsAreKept() throws Exception {
        AccidentalFactory accidental = new AccidentalFactory();
        RestTemplateCustomizer replace = template -> template.setRequestFactory(accidental);
        ClientHttpRequestInterceptor global = (request, body, execution) -> {
            request.getHeaders().add("X-Global", "yes");
            return execution.execute(request, body);
        };

        TemplatePingClient client = client(TemplatePingClient.class, serverUrl(), null, context ->
                context.registerBean(RestTemplateBuilder.class,
                        () -> new RestTemplateBuilder().additionalCustomizers(replace).additionalInterceptors(global)));

        assertThat(client.ping()).isEqualTo("pong");
        assertThat(accidental.calls).hasValue(0);
        assertThat(receivedHeaders.get()).containsEntry("x-global", "yes");
        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("Ignored the request factory " + AccidentalFactory.class.getName())
                .contains("RestTemplateBuilder customizer").contains("client TemplatePingClient"));
    }

    @Test
    void withoutCustomizersTheClientUsesItsOwnFactoryWithoutWarnings() throws Exception {
        assertThat(client(PingClient.class, serverUrl(), null, context -> { }).ping()).isEqualTo("pong");
        assertThat(client(TemplatePingClient.class, serverUrl(), null, context -> context.registerBean(
                RestTemplateBuilder.class, () -> new RestTemplateBuilder())).ping()).isEqualTo("pong");

        assertThat(hits).hasValue(2);
        assertThat(warnings()).isEmpty();
    }

    // ------------------------------------------------------------------ las otras políticas

    @Test
    void allowAcceptsAnyReplacementButWarns() throws Exception {
        AccidentalFactory accidental = new AccidentalFactory();

        PingClient client = client(PingClient.class, serverUrl(), "allow", context -> context.registerBean(
                RestClientCustomizer.class, () -> new GlobalTimeoutCustomizer(accidental)));

        assertThatThrownBy(client::ping).isInstanceOf(ResourceAccessException.class);
        assertThat(accidental.calls).hasValue(1);
        assertThat(hits).hasValue(0);
        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("replaced the request factory of client PingClient")
                .contains("the connection pool, timeouts and SSL of @RestHttpClient no longer apply"));
    }

    @Test
    void denyIgnoresEvenAMock() throws Exception {
        AtomicReference<MockRestServiceServer> mock = new AtomicReference<>();

        PingClient client = client(PingClient.class, serverUrl(), "DENY", context -> context.registerBean(
                RestClientCustomizer.class, () -> builder -> mock.set(MockRestServiceServer.bindTo(builder).build())));
        mock.get().expect(requestTo(serverUrl() + "/ping")).andRespond(withSuccess("mocked", MediaType.TEXT_PLAIN));

        assertThat(client.ping()).isEqualTo("pong");
        assertThat(hits).hasValue(1);
        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("Ignored the request factory").contains("currently 'deny'"));
    }

    @Test
    void anInvalidPolicyFailsWhenTheClientIsCreated() {
        assertThatThrownBy(() -> client(PingClient.class, serverUrl(), "sometimes", context -> { }))
                .isInstanceOf(ClientException.class)
                .hasMessage("rest-http-client.request-factory-override must be auto, allow or deny but was "
                        + "'sometimes'");
    }

    @Test
    void theReplacementIsAlsoDetectedInsideApply() throws Exception {
        AccidentalFactory accidental = new AccidentalFactory();

        PingClient client = client(PingClient.class, serverUrl(), null, context -> context.registerBean(
                RestClientCustomizer.class, () -> builder -> builder.apply(inner -> inner.requestFactory(accidental)
                        .defaultHeader("X-Inside-Apply", "1"))));

        assertThat(client.ping()).isEqualTo("pong");
        assertThat(accidental.calls).hasValue(0);
        assertThat(receivedHeaders.get()).containsEntry("x-inside-apply", "1");
        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("Ignored the request factory"));
    }

    @Test
    void anErrorThrownByTheBuilderReachesTheCallerUnwrappedAndTheClientIsNotCreated() {
        assertThatThrownBy(() -> client(PingClient.class, serverUrl(), null, context -> context.registerBean(
                RestClientCustomizer.class, () -> builder -> builder.requestInterceptor(null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(hits).hasValue(0);
    }

    @Test
    void resettingTheRequestFactoryToNullIsTreatedAsAReplacementToo() throws Exception {
        PingClient client = client(PingClient.class, serverUrl(), null, context -> context.registerBean(
                RestClientCustomizer.class, () -> builder -> builder.requestFactory(null)));

        assertThat(client.ping()).isEqualTo("pong");
        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("Ignored the request factory null (the default one)").contains("client PingClient"));
    }

    @Test
    void policyConversionAndTestDoubleDetection() {
        assertThat(RequestFactoryOverridePolicy.from(null)).isEqualTo(RequestFactoryOverridePolicy.AUTO);
        assertThat(RequestFactoryOverridePolicy.from("  ")).isEqualTo(RequestFactoryOverridePolicy.AUTO);
        assertThat(RequestFactoryOverridePolicy.from(" Allow ")).isEqualTo(RequestFactoryOverridePolicy.ALLOW);
        assertThat(RequestFactoryOverridePolicy.isTestDouble(new AccidentalFactory())).isFalse();
        assertThat(RequestFactoryOverridePolicy.isTestDouble(org.mockito.Mockito.mock(ClientHttpRequestFactory.class)))
                .isTrue();
    }

    @Test
    void aSpringWrapperIsLookedIntoThroughItsPublicDelegate() {
        ClientHttpRequestFactory mock = org.mockito.Mockito.mock(ClientHttpRequestFactory.class);

        assertThat(RequestFactoryOverridePolicy.isTestDouble(
                new org.springframework.http.client.BufferingClientHttpRequestFactory(mock))).isTrue();
        assertThat(RequestFactoryOverridePolicy.isTestDouble(new org.springframework.http.client
                .BufferingClientHttpRequestFactory(new org.springframework.http.client
                .BufferingClientHttpRequestFactory(mock)))).isTrue();
        assertThat(RequestFactoryOverridePolicy.isTestDouble(
                new org.springframework.http.client.BufferingClientHttpRequestFactory(new AccidentalFactory())))
                .isFalse();
    }
}
