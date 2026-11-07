package com.juniverse.client.handler;

import org.springframework.web.bind.annotation.GetMapping;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.enums.ClientType;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RestHttpClientFactoryBeanTest {

    @RestHttpClient(url = "http://localhost")
    interface RestClientBased {

        @GetMapping("/x")
        String get();
    }

    @RestHttpClient(url = "http://localhost", type = ClientType.REST_TEMPLATE, errorHandler = DefaultErrorHandler.class)
    interface RestTemplateBased {

        @GetMapping("/x")
        String get();
    }

    private static <T> RestHttpClientFactoryBean<T> factory(Class<T> type) {
        return new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(), new RestClientUtil(new SSLUtil()));
    }

    @Test
    void createsProxyBackedByRestClient() throws Exception {
        RestClientBased client = factory(RestClientBased.class).getObject();

        assertThat(Proxy.isProxyClass(client.getClass())).isTrue();
        assertThat(Proxy.getInvocationHandler(client)).isInstanceOf(RestHttpClientInvocationHandler.class);
    }

    @Test
    void createsProxyBackedByRestTemplate() throws Exception {
        RestTemplateBased client = factory(RestTemplateBased.class).getObject();

        assertThat(client).isInstanceOf(RestTemplateBased.class);
    }

    @Test
    void exposesTypeAndSingletonScope() {
        RestHttpClientFactoryBean<RestClientBased> factory = factory(RestClientBased.class);

        assertThat(factory.getObjectType()).isEqualTo(RestClientBased.class);
        assertThat(factory.isSingleton()).isTrue();
    }

    @Test
    void storesApplicationContext() {
        RestHttpClientFactoryBean<RestClientBased> factory = factory(RestClientBased.class);
        StaticApplicationContext context = new StaticApplicationContext();

        factory.setApplicationContext(context);

        assertThat(factory.getApplicationContext()).isSameAs(context);
    }

    @Test
    void rejectsNullInterface() {
        assertThatNullPointerException().isThrownBy(() -> factory(null));
    }
}
