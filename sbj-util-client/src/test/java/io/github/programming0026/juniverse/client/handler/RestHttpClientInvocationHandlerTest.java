package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.CircuitBreakerConfig;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import io.github.programming0026.juniverse.client.autoconfigure.RateLimiterConfig;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.autoconfigure.TimeLimiterConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.exception.DefaultErrorHandler;
import io.github.programming0026.juniverse.client.exception.ErrorHandler;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestHttpClientInvocationHandlerTest {

    private static final String BASE = "http://localhost/api";

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface UserClient {

        @GetMapping(value = "/users/{id}", headers = {"X-Static=abc", "malformed-header"})
        String getUser(@PathVariable("id") long id, @RequestParam("active") boolean active,
                       @RequestHeader("X-Trace") String trace);

        @GetMapping("/users/{id}")
        String getUserImplicitNames(@PathVariable long id);

        @GetMapping("/search")
        String search(@RequestParam("q") String query);

        @PostMapping("/users")
        String create(@RequestBody String body);

        @PutMapping("/users/{id}")
        String update(@PathVariable("id") long id, @RequestBody String body);

        @PatchMapping("/users/{id}")
        String patch(@PathVariable("id") long id, @RequestBody String body);

        @DeleteMapping("/users/{id}")
        String delete(@PathVariable("id") long id);

        @DeleteMapping("/users/{name}")
        String deleteByName(@PathVariable("name") String name);

        @GetMapping("/users")
        String list();
    }

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface SpringMappingClient {

        @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE},
                headers = {"X-Token=a=b", "X-Present", "!X-Absent", "X-Not!=1"})
        String create(@RequestBody String json);

        @RequestMapping(path = "/items/{id}", method = RequestMethod.HEAD)
        String head(@PathVariable("id") long id);

    }

    @RestHttpClient(url = BASE)
    interface NoHttpMethodClient {

        @RequestMapping("/no-method")
        String noMethod();
    }

    @RestHttpClient(url = BASE)
    interface NotMappedClient {

        String notMapped();
    }

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface ParamClient {

        @GetMapping("/items/{itemId}")
        String find(@PathVariable(name = "itemId") Long id,
                    @RequestParam(name = "page", defaultValue = "1") Integer page,
                    @RequestParam(name = "sort", required = false) String sort,
                    @RequestParam("lang") String lang,
                    @RequestHeader(name = "X-Tenant", defaultValue = "default") String tenant,
                    @RequestHeader(value = "X-Opt", required = false) String optional,
                    @RequestHeader String implicitName);

        @PostMapping("/items")
        String create(@RequestBody Object body);

        @PostMapping("/items/optional")
        String createOptional(@RequestBody(required = false) Object body);
    }

    @RestHttpClient(name = "retrying", url = BASE, retry = @RetryConfig(maxRetries = 3, retryDelay = 1))
    interface RetryingClient {

        @GetMapping("/flaky")
        String flaky();
    }

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            circuitBreaker = @CircuitBreakerConfig(enabled = true, minimumNumberOfCalls = 100),
            rateLimiter = @RateLimiterConfig(enabled = true),
            timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 5000))
    interface ResilientClient {

        @GetMapping("/ok")
        String ok();

        @GetMapping("/fail")
        String fail();
    }

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1),
            timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 50))
    interface SlowClient {

        @GetMapping("/slow")
        String slow();
    }

    /** Manejador de errores que nunca lanza, para que se propague la excepción HTTP original. */
    static class SilentErrorHandler implements ErrorHandler {

        @Override
        public void handleError(HttpStatusCode statusCode, String responseBody, Exception exception) {
            // vacío a propósito
        }

        @Override
        public boolean shouldRetry(HttpStatusCode statusCode) {
            return false;
        }

        @Override
        public boolean shouldRecordAsFailure(HttpStatusCode statusCode) {
            return false;
        }
    }

    private static <T> T proxy(Class<T> type, Object httpClient, ErrorHandler errorHandler) {
        RestHttpClientInvocationHandler handler = handler(type, httpClient, errorHandler);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static RestHttpClientInvocationHandler handler(Class<?> type, Object httpClient,
                                                           ErrorHandler errorHandler) {
        return new RestHttpClientInvocationHandler(httpClient, type.getAnnotation(RestHttpClient.class),
                errorHandler, type, new CircuitBreakerUtil());
    }

    @Test
    void restClientGetWithPathVariableQueryParamAndHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/users/5?active=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Static", "abc"))
                .andExpect(header("X-Trace", "t-1"))
                .andExpect(headerDoesNotExist("malformed-header"))
                .andRespond(withSuccess("alice", MediaType.TEXT_PLAIN));

        UserClient client = proxy(UserClient.class, builder.build(), new DefaultErrorHandler());

        assertThat(client.getUser(5, true, "t-1")).isEqualTo("alice");
        server.verify();
    }

    @Test
    void restClientSupportsAllHttpMethods() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/users")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string("new"))
                .andRespond(withSuccess("created", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/users/1")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().string("upd"))
                .andRespond(withSuccess("updated", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/users/1")).andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("patched", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/users/1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("deleted", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/users")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("all", MediaType.TEXT_PLAIN));

        UserClient client = proxy(UserClient.class, builder.build(), new DefaultErrorHandler());

        assertThat(client.create("new")).isEqualTo("created");
        assertThat(client.update(1, "upd")).isEqualTo("updated");
        assertThat(client.patch(1, "p")).isEqualTo("patched");
        assertThat(client.delete(1)).isEqualTo("deleted");
        assertThat(client.list()).isEqualTo("all");
        server.verify();
    }

    @Test
    void springMappingAttributesAreTranslatedToRequestHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/items"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", "application/json, text/plain"))
                .andExpect(header("X-Token", "a=b"))
                .andExpect(headerDoesNotExist("X-Present"))
                .andExpect(headerDoesNotExist("!X-Absent"))
                .andExpect(headerDoesNotExist("X-Not!"))
                .andExpect(headerDoesNotExist("X-Not"))
                .andExpect(content().string("{\"a\":1}"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        SpringMappingClient client = proxy(SpringMappingClient.class, builder.build(), new DefaultErrorHandler());

        assertThat(client.create("{\"a\":1}")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void requestMappingWithExplicitMethodIsSupported() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items/3")).andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess());

        SpringMappingClient client = proxy(SpringMappingClient.class, template, new DefaultErrorHandler());

        assertThat(client.head(3)).isNull();
        server.verify();
    }

    @Test
    void methodsWithoutHttpMethodAreRejected() {
        // Se rechazan al crear el cliente, no en la primera llamada.
        assertThatThrownBy(() -> proxy(NoHttpMethodClient.class, new RestTemplate(), new DefaultErrorHandler()))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("NoHttpMethodClient.noMethod")
                .hasMessageContaining("@GetMapping");
        assertThatThrownBy(() -> proxy(NotMappedClient.class, new RestTemplate(), new DefaultErrorHandler()))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("NotMappedClient.notMapped");
    }

    @Test
    void springParameterAnnotationsHonorNamesAndDefaults() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items/4?page=1&lang=es"))
                .andExpect(header("X-Tenant", "default"))
                .andExpect(header("implicitName", "v"))
                .andExpect(headerDoesNotExist("X-Opt"))
                .andRespond(withSuccess("defaults", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/items/4?page=2&sort=name&lang=en"))
                .andExpect(header("X-Tenant", "acme"))
                .andExpect(header("X-Opt", "yes"))
                .andRespond(withSuccess("explicit", MediaType.TEXT_PLAIN));

        ParamClient client = proxy(ParamClient.class, template, new DefaultErrorHandler());

        assertThat(client.find(4L, null, null, "es", null, null, "v")).isEqualTo("defaults");
        assertThat(client.find(4L, 2, "name", "en", "acme", "yes", "v")).isEqualTo("explicit");
        server.verify();
    }

    @Test
    void missingRequiredValuesAreRejected() {
        ParamClient client = proxy(ParamClient.class, new RestTemplate(), new DefaultErrorHandler());

        assertThatThrownBy(() -> client.find(null, 1, null, "es", null, null, "v"))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required path variable 'itemId' is null or empty in ParamClient.find");
        assertThatThrownBy(() -> client.find(1L, 1, null, null, null, null, "v"))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required request parameter 'lang' is null or empty in ParamClient.find");
        assertThatThrownBy(() -> client.find(1L, 1, null, "es", null, null, null))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required request header 'implicitName' is null or empty in ParamClient.find");
        assertThatThrownBy(() -> client.create(null))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required request body 'body' is null or empty in ParamClient.create");
    }

    @Test
    void optionalRequestBodyMayBeNull() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items/optional")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("empty", MediaType.TEXT_PLAIN));

        ParamClient client = proxy(ParamClient.class, template, new DefaultErrorHandler());

        assertThat(client.createOptional(null)).isEqualTo("empty");
        server.verify();
    }

    @Test
    void restTemplateGetWithPathVariableQueryParamAndHeaders() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/users/7?active=false"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Static", "abc"))
                .andExpect(header("X-Trace", "t-2"))
                .andRespond(withSuccess("bob", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/users")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string("payload"))
                .andRespond(withSuccess("created", MediaType.TEXT_PLAIN));

        UserClient client = proxy(UserClient.class, template, new DefaultErrorHandler());

        assertThat(client.getUser(7, false, "t-2")).isEqualTo("bob");
        assertThat(client.create("payload")).isEqualTo("created");
        server.verify();
    }

    @Test
    void parameterNamesAreUsedWhenAnnotationValueIsEmpty() throws Exception {
        // Requiere compilar con -parameters (lo activa spring-boot-starter-parent).
        Method m = UserClient.class.getMethod("getUserImplicitNames", long.class);
        assertThat(m.getParameters()[0].getName()).isEqualTo("id");
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/users/9")).andRespond(withSuccess("x", MediaType.TEXT_PLAIN));

        UserClient client = proxy(UserClient.class, template, new DefaultErrorHandler());

        assertThat(client.getUserImplicitNames(9)).isEqualTo("x");
        server.verify();
    }

    @Test
    void valuesAreEncodedExactlyOnceWithRestTemplate() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/search?q=a%26b%20c%3Dd"))
                .andRespond(withSuccess("found", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/users/a%20b%2Fc"))
                .andRespond(withSuccess("deleted", MediaType.TEXT_PLAIN));

        UserClient client = proxy(UserClient.class, template, new DefaultErrorHandler());

        assertThat(client.search("a&b c=d")).isEqualTo("found");
        assertThat(client.deleteByName("a b/c")).isEqualTo("deleted");
        server.verify();
    }

    @Test
    void valuesAreEncodedExactlyOnceWithRestClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/search?q=50%25%2Boff"))
                .andRespond(withSuccess("found", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/users/%7Bx%7D"))
                .andRespond(withSuccess("deleted", MediaType.TEXT_PLAIN));

        UserClient client = proxy(UserClient.class, builder.build(), new DefaultErrorHandler());

        assertThat(client.search("50%+off")).isEqualTo("found");
        assertThat(client.deleteByName("{x}")).isEqualTo("deleted");
        server.verify();
    }

    @Test
    void clientErrorIsTranslatedByErrorHandler() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/users/1")).andRespond(withStatus(HttpStatus.NOT_FOUND).body("nope"));

        UserClient client = proxy(UserClient.class, template, new DefaultErrorHandler());

        assertThatThrownBy(() -> client.delete(1))
                .isInstanceOf(HttpClientErrorException.class)
                .hasMessageContaining("Client error: nope")
                .hasNoCause();
    }

    @Test
    void originalExceptionIsRethrownWhenErrorHandlerDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/users/1")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        UserClient client = proxy(UserClient.class, builder.build(), new SilentErrorHandler());

        assertThatThrownBy(() -> client.delete(1)).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void clientErrorsAreNotRetried() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(1), requestTo(BASE + "/flaky")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        RetryingClient client = proxy(RetryingClient.class, template, new DefaultErrorHandler());

        assertThatThrownBy(client::flaky)
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode().value())
                        .isEqualTo(404));
        server.verify();
    }

    @Test
    void serverErrorsNotAllowedByErrorHandlerAreNotRetried() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(1), requestTo(BASE + "/flaky"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        RetryingClient client = proxy(RetryingClient.class, template, new DefaultErrorHandler());

        assertThatThrownBy(client::flaky).isInstanceOf(HttpServerErrorException.InternalServerError.class);
        server.verify();
    }

    @Test
    void retryableErrorsAreRetriedUntilAttemptsAreExhausted() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(3), requestTo(BASE + "/flaky")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        RetryingClient client = proxy(RetryingClient.class, template, new DefaultErrorHandler());

        assertThatThrownBy(client::flaky)
                .isInstanceOf(HttpServerErrorException.BadGateway.class)
                .hasMessageStartingWith("Server error:");
        server.verify();
    }

    @Test
    void retriesUntilSuccess() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(2), requestTo(BASE + "/flaky"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE + "/flaky")).andRespond(withSuccess("finally", MediaType.TEXT_PLAIN));

        RetryingClient client = proxy(RetryingClient.class, template, new DefaultErrorHandler());

        assertThat(client.flaky()).isEqualTo("finally");
        server.verify();
    }

    @Test
    void resilientClientSucceedsThroughAllDecorators() throws Throwable {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/ok")).andRespond(withSuccess("fine", MediaType.TEXT_PLAIN));

        RestHttpClientInvocationHandler handler = handler(ResilientClient.class, template,
                new DefaultErrorHandler());

        assertThat(handler.invoke(null, ResilientClient.class.getMethod("ok"), null)).isEqualTo("fine");
        server.verify();
    }

    @Test
    void resilientClientRethrowsServerErrorRecordedByCircuitBreaker() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/fail")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        RestHttpClientInvocationHandler handler = handler(ResilientClient.class, template,
                new DefaultErrorHandler());

        assertThatThrownBy(() -> handler.invoke(null, ResilientClient.class.getMethod("fail"), null))
                .isInstanceOf(HttpServerErrorException.InternalServerError.class);
    }

    @Test
    void timeLimiterAbortsSlowCalls() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/slow")).andRespond(request -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MockRestResponseCreators.withSuccess("late", MediaType.TEXT_PLAIN).createResponse(request);
        });

        SlowClient client = proxy(SlowClient.class, template, new DefaultErrorHandler());

        assertThatThrownBy(client::slow)
                .isInstanceOf(ClientException.class)
                .hasMessage("Request timed out after 50 ms")
                .cause()
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    void unsupportedHttpClientTypeFails() {
        UserClient client = proxy(UserClient.class, new Object(), new DefaultErrorHandler());

        assertThatThrownBy(() -> client.delete(1))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Unsupported HTTP client type");
    }

    @Test
    void objectMethodsUseProxyIdentity() {
        UserClient client = proxy(UserClient.class, new RestTemplate(), new DefaultErrorHandler());
        UserClient other = proxy(UserClient.class, new RestTemplate(), new DefaultErrorHandler());

        assertThat(client.equals(client)).isTrue();
        assertThat(client.equals(other)).isFalse();
        assertThat(client.equals(null)).isFalse();
        assertThat(client.hashCode()).isEqualTo(System.identityHashCode(client));
        assertThat(client.toString())
                .isEqualTo("RestHttpClient proxy for " + UserClient.class.getName() + " [" + BASE + "]");
    }
}
