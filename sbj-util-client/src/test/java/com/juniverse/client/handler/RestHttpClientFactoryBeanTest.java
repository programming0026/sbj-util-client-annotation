package com.juniverse.client.handler;

import org.springframework.web.bind.annotation.GetMapping;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.enums.ClientType;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** Dependencia que el manejador de errores recibe por inyección. */
    static class Translator {

        String translate(int status) {
            return "translated " + status;
        }
    }

    /** Manejador de errores con dependencias inyectadas por constructor (no tiene constructor sin args). */
    static class InjectedErrorHandler extends DefaultErrorHandler {

        private final Translator translator;

        InjectedErrorHandler(Translator translator) {
            this.translator = translator;
        }

        Translator translator() {
            return translator;
        }
    }

    @RestHttpClient(url = "http://localhost", errorHandler = InjectedErrorHandler.class)
    interface InjectedHandlerClient {

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
    void proxyIsCreatedOnlyOnce() throws Exception {
        RestHttpClientFactoryBean<RestClientBased> factory = factory(RestClientBased.class);

        assertThat(factory.getObject()).isSameAs(factory.getObject());
    }

    @Test
    void destroyClosesTheConnectionPool() throws Exception {
        RestHttpClientFactoryBean<RestTemplateBased> factory = factory(RestTemplateBased.class);
        RestTemplateBased client = factory.getObject();

        factory.destroy();
        factory.destroy();

        assertThatThrownBy(client::get).hasStackTraceContaining("shut down");
    }

    @Test
    void errorHandlerIsCreatedWithDependencyInjection() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Translator.class);
            context.refresh();
            RestHttpClientFactoryBean<InjectedHandlerClient> factory = factory(InjectedHandlerClient.class);
            factory.setApplicationContext(context);

            InjectedErrorHandler handler = errorHandlerOf(factory.getObject());

            assertThat(handler.translator()).isSameAs(context.getBean(Translator.class));
        }
    }

    @Test
    void existingErrorHandlerBeanIsReused() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            InjectedErrorHandler bean = new InjectedErrorHandler(new Translator());
            context.registerBean(InjectedErrorHandler.class, () -> bean);
            context.refresh();
            RestHttpClientFactoryBean<InjectedHandlerClient> factory = factory(InjectedHandlerClient.class);
            factory.setApplicationContext(context);

            assertThat(errorHandlerOf(factory.getObject())).isSameAs(bean);
        }
    }

    private static InjectedErrorHandler errorHandlerOf(Object proxy) {
        Object handler = Proxy.getInvocationHandler(proxy);
        return (InjectedErrorHandler) ReflectionTestUtils.getField(handler, "errorHandler");
    }

    @Test
    void rejectsNullInterface() {
        assertThatNullPointerException().isThrownBy(() -> factory(null));
    }
}
