package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.autoconfigure.TimeLimiterConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.exception.DefaultErrorHandler;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Reintentos según la idempotencia del método HTTP. */
class RestHttpClientIdempotencyTest {

    private static final String BASE = "http://localhost/api";

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 3, retryDelay = 1))
    interface OrdersClient {

        @PostMapping("/orders")
        String create(@RequestBody String order);

        @PatchMapping("/orders/1")
        String patch(@RequestBody String change);

        @PutMapping("/orders/1")
        String replace(@RequestBody String order);

        @DeleteMapping("/orders/1")
        String delete();
    }

    @RestHttpClient(name = "idempotent-orders", url = BASE,
            retry = @RetryConfig(maxRetries = 3, retryDelay = 1, retryNonIdempotent = true))
    interface IdempotentOrdersClient {

        @PostMapping("/orders")
        String create(@RequestBody String order);
    }

    @RestHttpClient(name = "timed-orders", url = BASE, retry = @RetryConfig(maxRetries = 3, retryDelay = 1),
            timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 100))
    interface TimedOrdersClient {

        @PostMapping("/orders")
        String create(@RequestBody String order);
    }

    private static <T> T proxy(Class<T> type, RestTemplate template) {
        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(template,
                type.getAnnotation(RestHttpClient.class), new DefaultErrorHandler(), type, new CircuitBreakerUtil());
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    @Test
    void postIsNotRetriedOnRetryableHttpStatus() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(1), requestTo(BASE + "/orders")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> proxy(OrdersClient.class, template).create("o"))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        server.verify();
    }

    @Test
    void patchIsNotRetriedWhenTheConnectionBreaksAfterSending() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(1), requestTo(BASE + "/orders/1"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> proxy(OrdersClient.class, template).patch("c"))
                .isInstanceOf(ResourceAccessException.class);
        server.verify();
    }

    @Test
    void postIsRetriedWhenTheConnectionCouldNotBeEstablished() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/orders")).andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo(BASE + "/orders")).andRespond(withException(new ConnectTimeoutException("timeout")));
        server.expect(requestTo(BASE + "/orders")).andRespond(withSuccess("created", MediaType.TEXT_PLAIN));

        assertThat(proxy(OrdersClient.class, template).create("o")).isEqualTo("created");
        server.verify();
    }

    @Test
    void idempotentMethodsAreRetriedOnRetryableHttpStatus() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(2), requestTo(BASE + "/orders/1")).andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE + "/orders/1")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("replaced", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/orders/1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withException(new IOException("Connection reset")));
        server.expect(requestTo(BASE + "/orders/1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("deleted", MediaType.TEXT_PLAIN));

        OrdersClient client = proxy(OrdersClient.class, template);

        assertThat(client.replace("o")).isEqualTo("replaced");
        assertThat(client.delete()).isEqualTo("deleted");
        server.verify();
    }

    @Test
    void retryNonIdempotentRestoresFullRetriesForPost() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(2), requestTo(BASE + "/orders")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(BASE + "/orders")).andRespond(withSuccess("created", MediaType.TEXT_PLAIN));

        assertThat(proxy(IdempotentOrdersClient.class, template).create("o")).isEqualTo("created");
        server.verify();
    }

    @Test
    void postIsNotRetriedAfterATimeout() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(1), requestTo(BASE + "/orders")).andRespond(request -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MockRestResponseCreators.withSuccess("late", MediaType.TEXT_PLAIN).createResponse(request);
        });

        assertThatThrownBy(() -> proxy(TimedOrdersClient.class, template).create("o"))
                .isInstanceOf(ClientException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        server.verify();
    }

    @Test
    void connectionFailuresAreDetectedThroughTheCauseChain() {
        assertThat(CircuitBreakerUtil.isConnectionFailure(
                new ResourceAccessException("I/O error", new ConnectException("refused")))).isTrue();
        assertThat(CircuitBreakerUtil.isConnectionFailure(
                new ResourceAccessException("I/O error", new ConnectTimeoutException("connect")))).isTrue();
        assertThat(CircuitBreakerUtil.isConnectionFailure(
                new ResourceAccessException("I/O error", new ConnectionRequestTimeoutException("pool")))).isTrue();
        assertThat(CircuitBreakerUtil.isConnectionFailure(
                new ResourceAccessException("I/O error", new SocketTimeoutException("read")))).isFalse();
        assertThat(CircuitBreakerUtil.isConnectionFailure(new IOException("reset"))).isFalse();
    }

    @Test
    void idempotentMethodsFollowRfc9110() {
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.GET)).isTrue();
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.HEAD)).isTrue();
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.OPTIONS)).isTrue();
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.TRACE)).isTrue();
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.PUT)).isTrue();
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.DELETE)).isTrue();
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.POST)).isFalse();
        assertThat(RestHttpClientInvocationHandler.isIdempotent(HttpMethod.PATCH)).isFalse();
    }
}
