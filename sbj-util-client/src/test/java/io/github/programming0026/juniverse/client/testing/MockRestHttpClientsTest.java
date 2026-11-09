package io.github.programming0026.juniverse.client.testing;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.enums.ClientType;
import io.github.programming0026.juniverse.client.handler.RestHttpClientFactoryBean;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.SSLUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** {@link MockRestHttpClients}: un servidor simulado por cliente, aunque haya varios en la aplicación. */
class MockRestHttpClientsTest {

    @RestHttpClient(url = "http://users.test", retry = @RetryConfig(maxRetries = 1))
    interface UsersClient {

        @GetMapping("/users/{id}")
        String find(@PathVariable("id") long id);
    }

    @RestHttpClient(url = "http://payments.test", retry = @RetryConfig(maxRetries = 1))
    interface PaymentsClient {

        @PostMapping("/charge")
        String charge();
    }

    @RestHttpClient(url = "http://legacy.test", name = "legacy", type = ClientType.REST_TEMPLATE,
            retry = @RetryConfig(maxRetries = 1))
    interface LegacyClient {

        @GetMapping("/status")
        String status();
    }

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private MockRestHttpClients mocks(MockRestHttpClients mocks) {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("mocks", MockRestHttpClients.class, () -> mocks);
        context.refresh();
        return mocks;
    }

    private <T> T client(Class<T> type) throws Exception {
        RestHttpClientFactoryBean<T> factory = new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(),
                new RestClientUtil(new SSLUtil()));
        factory.setApplicationContext(context);
        return factory.getObject();
    }

    @Test
    void eachClientGetsItsOwnServer() throws Exception {
        MockRestHttpClients mocks = mocks(new MockRestHttpClients());
        UsersClient users = client(UsersClient.class);
        PaymentsClient payments = client(PaymentsClient.class);
        LegacyClient legacy = client(LegacyClient.class);

        mocks.server(UsersClient.class).expect(requestTo("http://users.test/users/7"))
                .andRespond(withSuccess("ana", MediaType.TEXT_PLAIN));
        mocks.server(PaymentsClient.class).expect(requestTo("http://payments.test/charge"))
                .andExpect(method(HttpMethod.POST)).andRespond(withSuccess("paid", MediaType.TEXT_PLAIN));
        mocks.server("legacy").expect(requestTo("http://legacy.test/status"))
                .andRespond(withSuccess("up", MediaType.TEXT_PLAIN));

        assertThat(users.find(7)).isEqualTo("ana");
        assertThat(payments.charge()).isEqualTo("paid");
        assertThat(legacy.status()).isEqualTo("up");
        mocks.verify();
        assertThat(mocks.clientNames()).containsExactly("PaymentsClient", "UsersClient", "legacy");
    }

    @Test
    void anUnexpectedCallOnOneClientDoesNotConsumeTheExpectationsOfAnother() throws Exception {
        MockRestHttpClients mocks = mocks(new MockRestHttpClients());
        UsersClient users = client(UsersClient.class);
        client(PaymentsClient.class);

        mocks.server(PaymentsClient.class).expect(requestTo("http://payments.test/charge"))
                .andRespond(withSuccess("paid", MediaType.TEXT_PLAIN));

       assertThatThrownBy(() -> users.find(1)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(mocks::verify).isInstanceOf(AssertionError.class)
                .hasMessageContaining("[PaymentsClient]").hasMessageNotContaining("[UsersClient]");
    }

    @Test
    void verifyReportsEveryClientWithPendingExpectations() throws Exception {
        MockRestHttpClients mocks = mocks(new MockRestHttpClients());
        client(UsersClient.class);
        client(PaymentsClient.class);
        mocks.server(UsersClient.class).expect(requestTo("http://users.test/users/1"))
                .andRespond(withSuccess("x", MediaType.TEXT_PLAIN));
        mocks.server(PaymentsClient.class).expect(requestTo("http://payments.test/charge"))
                .andRespond(withSuccess("x", MediaType.TEXT_PLAIN));

        assertThatThrownBy(mocks::verify).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Unmet expectations in 2 client(s)")
                .hasMessageContaining("[PaymentsClient]").hasMessageContaining("[UsersClient]");
    }

    @Test
    void anUnknownClientNameListsTheKnownOnes() throws Exception {
        MockRestHttpClients mocks = mocks(new MockRestHttpClients());
        client(UsersClient.class);

        assertThatThrownBy(() -> mocks.server("Nope")).isInstanceOf(IllegalStateException.class)
                .hasMessage("There is no mocked client named 'Nope'. Known clients: [UsersClient]");
    }

    @Test
    void resetClearsTheExpectationsOfEveryClient() throws Exception {
        MockRestHttpClients mocks = mocks(new MockRestHttpClients());
        UsersClient users = client(UsersClient.class);
        mocks.server(UsersClient.class).expect(requestTo("http://users.test/users/1"))
                .andRespond(withSuccess("x", MediaType.TEXT_PLAIN));

        mocks.reset();
        mocks.verify();
        mocks.server(UsersClient.class).expect(requestTo("http://users.test/users/2"))
                .andRespond(withSuccess("two", MediaType.TEXT_PLAIN));

        assertThat(users.find(2)).isEqualTo("two");
        mocks.verify();
    }

    @Test
    void theOptionsAreAppliedToEveryServer() throws Exception {
        MockRestHttpClients mocks = mocks(new MockRestHttpClients().bufferContent().ignoreExpectOrder());
        PaymentsClient payments = client(PaymentsClient.class);
        UsersClient users = client(UsersClient.class);

        mocks.server(UsersClient.class).expect(requestTo("http://users.test/users/1"))
                .andRespond(withSuccess("one", MediaType.TEXT_PLAIN));
        mocks.server(UsersClient.class).expect(requestTo("http://users.test/users/2"))
                .andRespond(withSuccess("two", MediaType.TEXT_PLAIN));

       assertThat(users.find(2)).isEqualTo("two");
        assertThat(users.find(1)).isEqualTo("one");
        assertThat(payments).isNotNull();
        mocks.verify();
    }
}
