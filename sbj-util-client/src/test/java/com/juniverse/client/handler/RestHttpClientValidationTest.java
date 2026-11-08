package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.handler.invalid.InvalidClients;
import com.juniverse.client.util.CircuitBreakerUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Validación de los clientes al crearlos y análisis de los métodos una sola vez. */
class RestHttpClientValidationTest {

    private static final String BASE = "http://localhost/api";

    @RestHttpClient(url = BASE)
    interface UnannotatedParameterClient {

        @GetMapping("/items")
        String search(String query);
    }

    @RestHttpClient(url = BASE)
    interface TwoBodiesClient {

        @PostMapping("/items")
        String create(@RequestBody String first, @RequestBody String second);
    }

    @RestHttpClient(url = BASE)
    interface UnboundPathVariableClient {

        @GetMapping("/orgs/{org}/items/{id}")
        String item(@PathVariable("org") String org);
    }

    @RestHttpClient(url = BASE)
    interface UnknownPathVariableClient {

        @GetMapping("/items/{id}")
        String item(@PathVariable("id") long id, @PathVariable("version") int version);
    }

    @RestHttpClient(url = BASE)
    interface ValidClient {

        @GetMapping("${test.prefix}/items/{id}")
        String item(@PathVariable("id") long id,
                    @RequestParam(name = "lang", defaultValue = "${test.lang}") String lang,
                    @RequestHeader(name = "X-Trace", required = false) String trace);

        @GetMapping("/orgs/{org}/items/{id}")
        String byMap(@PathVariable Map<String, Object> variables);

        default String helper() {
            return "not an HTTP method";
        }

        static String utility() {
            return "not an HTTP method";
        }
    }

    private static RestHttpClientInvocationHandler handler(Class<?> type, String url, UnaryOperator<String> resolver) {
        RestHttpClient config = type.getAnnotation(RestHttpClient.class);
        if (url != null) {
            Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(config);
            attributes.put("url", url);
            config = AnnotationUtils.synthesizeAnnotation(attributes, RestHttpClient.class, null);
        }
        return new RestHttpClientInvocationHandler(new RestTemplate(), config, new DefaultErrorHandler(), type,
                new CircuitBreakerUtil(), resolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/api", "localhost:8080", "ftp://files.example.com", "http://", "http://bad host"})
    void invalidBaseUrlsAreRejected(String url) {
        assertThatThrownBy(() -> handler(ValidClient.class, url, this::resolve))
                .isInstanceOf(ClientException.class)
                .hasMessage("Client ValidClient must declare an absolute http(s) url, but was '" + url + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "https://api.example.com:8443/v1/", "HTTP://LOCALHOST"})
    void absoluteHttpUrlsAreAccepted(String url) {
        assertThatCode(() -> handler(ValidClient.class, url, this::resolve)).doesNotThrowAnyException();
    }

    @Test
    void unannotatedParametersAreRejected() {
        assertThatThrownBy(() -> handler(UnannotatedParameterClient.class, null, UnaryOperator.identity()))
                .isInstanceOf(ClientException.class)
                .hasMessage("Parameter 'query' of method UnannotatedParameterClient.search must be annotated with "
                        + "@PathVariable, @RequestParam, @RequestHeader, @RequestBody or @RequestPart");
    }

    @Test
    void moreThanOneBodyIsRejected() {
        assertThatThrownBy(() -> handler(TwoBodiesClient.class, null, UnaryOperator.identity()))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method TwoBodiesClient.create declares more than one @RequestBody");
    }

    @Test
    void pathVariablesWithoutParameterAreRejected() {
        assertThatThrownBy(() -> handler(UnboundPathVariableClient.class, null, UnaryOperator.identity()))
                .isInstanceOf(ClientException.class)
                .hasMessage("Path variable 'id' of method UnboundPathVariableClient.item is not bound to any "
                        + "@PathVariable parameter");
    }

    @Test
    void pathVariableParametersMissingFromThePathAreRejected() {
        assertThatThrownBy(() -> handler(UnknownPathVariableClient.class, null, UnaryOperator.identity()))
                .isInstanceOf(ClientException.class)
                .hasMessage("@PathVariable 'version' of method UnknownPathVariableClient.item does not appear in "
                        + "path '/items/{id}'");
    }

    @Test
    void invalidClientFailsContextStartupEvenIfNobodyInjectsIt() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(InvalidClients.Config.class);

            assertThatThrownBy(context::refresh)
                    .rootCause()
                    .isInstanceOf(ClientException.class)
                    .hasMessageContaining("Path variable 'id' of method UnboundPathVariableClient.item");
        }
    }

    @Test
    void mappingsArePlaceholderResolvedOnceNotOnEveryCall() {
        AtomicInteger resolutions = new AtomicInteger();
        UnaryOperator<String> countingResolver = value -> {
            resolutions.incrementAndGet();
            return resolve(value);
        };
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(3), requestTo(BASE + "/v2/items/1?lang=es"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(template,
                ValidClient.class.getAnnotation(RestHttpClient.class), new DefaultErrorHandler(), ValidClient.class,
                new CircuitBreakerUtil(), countingResolver);
        ValidClient client = (ValidClient) java.lang.reflect.Proxy.newProxyInstance(
                ValidClient.class.getClassLoader(), new Class<?>[]{ValidClient.class}, handler);
        int afterCreation = resolutions.get();

        for (int i = 0; i < 3; i++) {
            assertThat(client.item(1, null, null)).isEqualTo("ok");
        }

        assertThat(afterCreation).isPositive();
        assertThat(resolutions.get()).isEqualTo(afterCreation);
        assertThat(client.helper()).isEqualTo("not an HTTP method");
        server.verify();
    }

    private String resolve(String value) {
        return value.replace("${test.prefix}", "/v2").replace("${test.lang}", "es");
    }
}
