package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.config.JsonMapperConfigurer;
import com.juniverse.client.enums.ClientType;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code jsonConfig}: el JSON de un cliente se personaliza sin afectar a los demás ni a la aplicación. */
class RestHttpClientJsonConfigTest {

    /** Tipo de la API: la API usa snake_case. */
    public record Person(String firstName, Integer age) {
    }

    /** Configurador del ejemplo del README. */
    public static class SnakeCase implements JsonMapperConfigurer {
        @Override
        public void configure(JsonMapper.Builder builder) {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
    }

    @RestHttpClient(url = "${t.url}", jsonConfig = SnakeCase.class, retry = @RetryConfig(maxRetries = 1,
            retryDelay = 1))
    interface SnakeClient {
        @GetMapping("/person")
        Person person();

        @PostMapping("/echo")
        String echo(@RequestBody Person person);
    }

    /** No cambia nada: sirve para ver la base del mapper. */
    public static class Untouched implements JsonMapperConfigurer {
        @Override
        public void configure(JsonMapper.Builder builder) {
            // sin cambios
        }
    }

    @RestHttpClient(url = "${t.url}", jsonConfig = Untouched.class, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface UntouchedClient {
        @GetMapping("/person")
        Person person();
    }

    @RestHttpClient(url = "${t.url}", type = ClientType.REST_TEMPLATE, jsonConfig = SnakeCase.class,
            retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface SnakeTemplateClient {
        @GetMapping("/person")
        Person person();

        @PostMapping("/echo")
        String echo(@RequestBody Person person);
    }

    @RestHttpClient(url = "${t.url}", retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface PlainClient {
        @GetMapping("/person")
        Person person();

        @PostMapping("/echo")
        String echo(@RequestBody Person person);
    }

    /** Lo declara como bean la prueba: necesita una dependencia. */
    public static class Suffix {
        final String value = "_x";
    }

    /** Configurador con dependencias: se crea con inyección. */
    public static class InjectedConfigurer implements JsonMapperConfigurer {
        static final AtomicReference<String> SEEN = new AtomicReference<>();

        private final Suffix suffix;

        public InjectedConfigurer(Suffix suffix) {
            this.suffix = suffix;
        }

        @Override
        public void configure(JsonMapper.Builder builder) {
            SEEN.set(suffix.value);
        }
    }

    @RestHttpClient(url = "${t.url}", jsonConfig = InjectedConfigurer.class,
            retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface InjectedClient {
        @GetMapping("/person")
        Person person();
    }

    private HttpServer server;

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/person", exchange -> reply(exchange, "{\"first_name\":\"Ana\",\"age\":30,"
                + "\"firstName\":\"camel\",\"unknown\":true}"));
        server.createContext("/echo", exchange -> {
            byte[] echoed = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\"", "'").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, echoed.length);
            exchange.getResponseBody().write(echoed);
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

    private static void reply(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private <T> T client(Class<T> type, Object... beans) throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("t.url", "http://127.0.0.1:" + server.getAddress().getPort())));
        for (Object bean : beans) {
            context.registerBean(bean.getClass().getName(), (Class<Object>) bean.getClass(), () -> bean);
        }
        context.refresh();
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    @Test
    void aRestClientReadsSnakeCaseAndIgnoresUnknownProperties() throws Exception {
        assertThat(client(SnakeClient.class).person()).isEqualTo(new Person("Ana", 30));
    }

    @Test
    void aRestClientWritesSnakeCase() throws Exception {
        assertThat(client(SnakeClient.class).echo(new Person("Ana", 30)))
                .isEqualTo("{'first_name':'Ana','age':30}");
    }

    @Test
    void aRestTemplateReadsAndWritesSnakeCaseToo() throws Exception {
        SnakeTemplateClient client = client(SnakeTemplateClient.class);

        assertThat(client.person()).isEqualTo(new Person("Ana", 30));
        assertThat(client.echo(new Person("Ana", 30))).isEqualTo("{'first_name':'Ana','age':30}");
    }

    @Test
    void aClientWithoutJsonConfigKeepsTheDefaults() throws Exception {
        PlainClient client = client(PlainClient.class);

        assertThat(client.echo(new Person("Ana", 30))).isEqualTo("{'firstName':'Ana','age':30}");
        // Sin snake_case, "firstName" es la propiedad que se lee (y no first_name).
        assertThat(client.person()).isEqualTo(new Person("camel", 30));
    }

    @Test
    void withoutAnApplicationMapperTheBaseIsSpringsDefaultWhichToleratesUnknownProperties() throws Exception {
        assertThat(client(UntouchedClient.class).person()).isEqualTo(new Person("camel", 30));
    }

    @Test
    void theClientStartsFromTheApplicationJsonMapper() throws Exception {
        JsonMapper application = JsonMapper.builder()
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL))
                .build();

        assertThat(client(SnakeClient.class, application).echo(new Person("Ana", null)))
                .isEqualTo("{'first_name':'Ana'}");
    }

    @Test
    void theApplicationJsonMapperIsLeftUntouched() throws Exception {
        JsonMapper application = JsonMapper.builder().build();

        client(SnakeClient.class, application).echo(new Person("Ana", 30));

        assertThat(application.writeValueAsString(new Person("Ana", 30))).isEqualTo("{\"firstName\":\"Ana\","
                + "\"age\":30}");
    }

    @Test
    void theConfigurerIsCreatedWithDependencyInjection() throws Exception {
        InjectedConfigurer.SEEN.set(null);

        client(InjectedClient.class, new Suffix()).person();

        assertThat(InjectedConfigurer.SEEN).hasValue("_x");
    }
}
