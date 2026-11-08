package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.CircuitBreakerConfig;
import com.juniverse.client.autoconfigure.RateLimiterConfig;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.autoconfigure.TimeLimiterConfig;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.util.CircuitBreakerUtil;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Tipos de retorno, mapping de la interfaz, métodos de Object/default y orden de la resiliencia. */
class RestHttpClientInvocationHandlerBehaviorTest {

    private static final String BASE = "http://localhost/api";

    /** Elemento de respuesta JSON. */
    record Item(String name, int stock) {
    }

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface ReturnTypesClient {

        @GetMapping("/items")
        List<Item> list();

        @GetMapping("/items/first")
        ResponseEntity<Item> first();

        @GetMapping("/count")
        int count();

        @DeleteMapping("/items")
        void deleteAll();

        @DeleteMapping("/items/entity")
        ResponseEntity<Void> deleteWithStatus();

        default String describe() {
            return "items client";
        }
    }

    @RestHttpClient(url = BASE + "/", retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    @RequestMapping(path = "v1", method = RequestMethod.GET, headers = "X-Client=items",
            params = "tenant=acme", produces = MediaType.APPLICATION_JSON_VALUE)
    interface PrefixedClient {

        @GetMapping(path = "/stock", params = {"tenant=other", "!debug"}, headers = "X-Client=stock")
        String stock();

        @RequestMapping("summary")
        String summary();

        @GetMapping("/orgs/{org}/items/{id}")
        String byPath(@PathVariable Map<String, Object> variables);
    }

    @RestHttpClient(url = BASE)
    interface TwoPathsClient {

        @GetMapping(path = {"/a", "/b"})
        String twoPaths();
    }

    @RestHttpClient(url = BASE)
    interface TwoMethodsClient {

        @RequestMapping(path = "/c", method = {RequestMethod.GET, RequestMethod.POST})
        String twoMethods();
    }

    @RestHttpClient(name = "cb-attempts", url = BASE, retry = @RetryConfig(maxRetries = 2, retryDelay = 1),
            circuitBreaker = @CircuitBreakerConfig(enabled = true, slidingWindowSize = 2, minimumNumberOfCalls = 2,
                    failureRateThreshold = 50, waitDurationInOpenState = 60000))
    interface CircuitClient {

        @GetMapping("/unstable")
        String unstable();

        @GetMapping("/missing")
        String missing();
    }

    @RestHttpClient(name = "rl-attempts", url = BASE, retry = @RetryConfig(maxRetries = 3, retryDelay = 1),
            rateLimiter = @RateLimiterConfig(enabled = true, limitForPeriod = 1, limitRefreshPeriod = 60000,
                    timeoutDuration = 0))
    interface RateLimitedClient {

        @GetMapping("/limited")
        String limited();
    }

    @RestHttpClient(name = "tl-attempts", url = BASE, retry = @RetryConfig(maxRetries = 2, retryDelay = 1),
            timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 100))
    interface TimedClient {

        @GetMapping("/timed")
        String timed();
    }

    private static <T> T proxy(Class<T> type, Object httpClient) {
        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(httpClient,
                type.getAnnotation(RestHttpClient.class), new DefaultErrorHandler(), type, new CircuitBreakerUtil());
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    @Test
    void genericReturnTypesKeepTheirTypeArguments() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/items")).andRespond(withSuccess(
                "[{\"name\":\"a\",\"stock\":1},{\"name\":\"b\",\"stock\":2}]", MediaType.APPLICATION_JSON));

        List<Item> items = proxy(ReturnTypesClient.class, builder.build()).list();

        assertThat(items).containsExactly(new Item("a", 1), new Item("b", 2));
        server.verify();
    }

    @Test
    void genericReturnTypesWorkWithRestTemplate() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items")).andRespond(withSuccess(
                "[{\"name\":\"a\",\"stock\":1}]", MediaType.APPLICATION_JSON));

        assertThat(proxy(ReturnTypesClient.class, template).list()).containsExactly(new Item("a", 1));
    }

    @Test
    void responseEntityAndVoidReturnTypes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/items/first")).andRespond(withSuccess(
                "{\"name\":\"a\",\"stock\":3}", MediaType.APPLICATION_JSON).header("X-Total", "9"));
        server.expect(requestTo(BASE + "/items")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(BASE + "/items/entity")).andRespond(withStatus(HttpStatus.ACCEPTED));

        ReturnTypesClient client = proxy(ReturnTypesClient.class, builder.build());

        ResponseEntity<Item> first = client.first();
        assertThat(first.getBody()).isEqualTo(new Item("a", 3));
        assertThat(first.getHeaders().getFirst("X-Total")).isEqualTo("9");
        client.deleteAll();
        assertThat(client.deleteWithStatus().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        server.verify();
    }

    @Test
    void responseEntityAndVoidWorkWithRestTemplate() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items")).andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(BASE + "/items/entity")).andRespond(withStatus(HttpStatus.ACCEPTED));

        ReturnTypesClient client = proxy(ReturnTypesClient.class, template);

        client.deleteAll();
        assertThat(client.deleteWithStatus().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        server.verify();
    }

    @Test
    void emptyBodyForPrimitiveReturnTypeIsRejected() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/count")).andRespond(withSuccess());

        assertThatThrownBy(() -> proxy(ReturnTypesClient.class, template).count())
                .isInstanceOf(ClientException.class)
                .hasMessage("Empty response body for method ReturnTypesClient.count returning primitive int");
    }

    @Test
    void defaultMethodsRunWithoutHttpCall() {
        assertThat(proxy(ReturnTypesClient.class, new RestTemplate()).describe()).isEqualTo("items client");
    }

    @Test
    void interfaceLevelMappingProvidesPrefixDefaultsAndParams() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/v1/stock?tenant=other"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Client", "stock"))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("5", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/v1/summary?tenant=acme"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Client", "items"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        PrefixedClient client = proxy(PrefixedClient.class, template);

        assertThat(client.stock()).isEqualTo("5");
        assertThat(client.summary()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void ambiguousMappingsAreRejected() {
        assertThatThrownBy(() -> proxy(TwoPathsClient.class, new RestTemplate()))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method TwoPathsClient.twoPaths declares more than one path");
        assertThatThrownBy(() -> proxy(TwoMethodsClient.class, new RestTemplate()))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method TwoMethodsClient.twoMethods declares more than one HTTP method");
    }

    @Test
    void missingUriVariableIsReportedAsClientException() {
        PrefixedClient client = proxy(PrefixedClient.class, new RestTemplate());

        assertThatThrownBy(() -> client.byPath(Map.of("org", "acme")))
                .isInstanceOf(ClientException.class)
                .hasMessageStartingWith("Cannot build request URI for PrefixedClient.byPath")
                .hasMessageContaining("id");
    }

    @Test
    void pathsAreJoinedWithExactlyOneSlash() {
        assertThat(MethodMetadata.joinPath("http://h/api/", "/items")).isEqualTo("http://h/api/items");
        assertThat(MethodMetadata.joinPath("http://h/api", "items")).isEqualTo("http://h/api/items");
        assertThat(MethodMetadata.joinPath("http://h/api", "/items")).isEqualTo("http://h/api/items");
        assertThat(MethodMetadata.joinPath("http://h/api/", "")).isEqualTo("http://h/api/");
        assertThat(MethodMetadata.joinPath("", "/items")).isEqualTo("/items");
    }

    @Test
    void circuitBreakerCountsEveryAttemptAndStopsRetriesWhenOpen() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        // Una sola llamada lógica hace 2 intentos (retry): el circuito ve 2 fallas y se abre.
        server.expect(times(2), requestTo(BASE + "/unstable")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        CircuitClient client = proxy(CircuitClient.class, template);

        assertThatThrownBy(client::unstable)
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class)
                .hasMessageStartingWith("Server error:");
        // Con el circuito abierto no se hace ninguna petición ni se reintenta.
        assertThatThrownBy(client::unstable).isInstanceOf(CallNotPermittedException.class);
        server.verify();
    }

    @Test
    void clientErrorsDoNotOpenTheCircuit() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(3), requestTo(BASE + "/missing")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CircuitClient client = proxy(CircuitClient.class, template);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(client::missing).isInstanceOf(HttpClientErrorException.NotFound.class);
        }
        server.verify();
    }

    @Test
    void everyRetryAttemptConsumesARateLimiterPermit() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(1), requestTo(BASE + "/limited")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        RateLimitedClient client = proxy(RateLimitedClient.class, template);

        // El primer intento usa el único permiso; el reintento es rechazado y no se reintenta de nuevo.
        assertThatThrownBy(client::limited).isInstanceOf(RequestNotPermitted.class);
        server.verify();
    }

    @Test
    void timeLimitApplicesPerAttemptAndTimeoutsAreRetried() throws Exception {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        server.expect(requestTo(BASE + "/timed")).andRespond(request -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                finished.countDown();
            }
            return MockRestResponseCreators.withSuccess("late", MediaType.TEXT_PLAIN).createResponse(request);
        });
        server.expect(requestTo(BASE + "/timed")).andRespond(withSuccess("fast", MediaType.TEXT_PLAIN));

        assertThat(proxy(TimedClient.class, template).timed()).isEqualTo("fast");
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        // La petición que excedió el tiempo se interrumpió en vez de seguir corriendo.
        assertThat(interrupted).isTrue();
    }
}
