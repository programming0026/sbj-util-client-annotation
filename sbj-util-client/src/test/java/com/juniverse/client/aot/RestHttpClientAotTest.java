package com.juniverse.client.aot;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.SSLConfig;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.exception.ErrorHandler;
import com.juniverse.client.handler.RestHttpClientFactoryBean;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los hints que se declaran para la imagen nativa cubren todo lo que la librería usa por reflexión. */
class RestHttpClientAotTest {

    /** Cuerpo de respuesta propio de la aplicación. */
    public record Item(String id, Detail detail) {
    }

    /** Tipo anidado dentro de {@link Item}. */
    public record Detail(String name) {
    }

    /** Cuerpo de petición propio de la aplicación. */
    public record NewItem(String name) {
    }

    /** Interceptor que la librería instancia por reflexión. */
    public static class NoopInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            return execution.execute(request, body);
        }
    }

    /** Manejador de errores propio. */
    public static class CustomErrorHandler extends DefaultErrorHandler {
    }

    /** Fallback propio. */
    public static class ItemsFallback implements ItemsClient {
        @Override
        public Item find(String id) {
            return null;
        }

        @Override
        public List<Item> list() {
            return List.of();
        }

        @Override
        public Item create(NewItem item) {
            return null;
        }
    }

    /** Cliente de prueba que usa todas las funciones que requieren reflexión. */
    @RestHttpClient(url = "http://localhost", errorHandler = CustomErrorHandler.class,
            interceptors = NoopInterceptor.class, fallback = ItemsFallback.class, ssl = @SSLConfig)
    public interface ItemsClient {
        @GetMapping("/items/{id}")
        Item find(@PathVariable("id") String id);

        @GetMapping("/items")
        List<Item> list();

        @PostMapping("/items")
        Item create(@RequestBody NewItem item);
    }

    private static RuntimeHints hintsFor(Class<?> client) {
        RuntimeHints hints = new RuntimeHints();
        RestHttpClientRuntimeHints.register(hints, client);
        return hints;
    }

    @Test
    void registersTheJdkProxyOfTheClientInterface() {
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(ItemsClient.class)).accepts(hintsFor(ItemsClient.class));
    }

    @Test
    void registersTheMethodsOfTheClientInterface() throws NoSuchMethodException {
        RuntimeHints hints = hintsFor(ItemsClient.class);
        for (Method method : ItemsClient.class.getMethods()) {
            assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method)).accepts(hints);
        }
    }

    @Test
    void registersTheLibraryAnnotationsAndTheirSynthesizedProxies() {
        RuntimeHints hints = hintsFor(ItemsClient.class);
        assertThat(RuntimeHintsPredicates.reflection().onType(RestHttpClient.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(RestHttpClient.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(SSLConfig.class)).accepts(hints);
    }

    @Test
    void registersTheSpringWebAnnotationsAndTheirMetaAnnotations() {
        RuntimeHints hints = hintsFor(ItemsClient.class);
        assertThat(RuntimeHintsPredicates.reflection().onType(GetMapping.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(PathVariable.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(org.springframework.web.bind.annotation.RequestMapping.class))
                .accepts(hints);
    }

    @Test
    void registersTheClassesTheLibraryInstantiates() throws NoSuchMethodException {
        RuntimeHints hints = hintsFor(ItemsClient.class);
        for (Class<?> type : List.of(CustomErrorHandler.class, NoopInterceptor.class, ItemsFallback.class)) {
            assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(type.getDeclaredConstructor()))
                    .accepts(hints);
        }
    }

    @Test
    void doesNotRegisterTheDefaultErrorHandlerWhenAnotherOneIsDeclared() {
        assertThat(RuntimeHintsPredicates.reflection().onType(ErrorHandler.class)).rejects(hintsFor(ItemsClient.class));
    }

    @Test
    void registersTheBodyTypesForJacksonIncludingNestedAndGenericOnes() throws NoSuchMethodException {
        RuntimeHints hints = hintsFor(ItemsClient.class);
        for (Class<?> type : List.of(Item.class, Detail.class, NewItem.class)) {
            assertThat(RuntimeHintsPredicates.reflection().onType(type)).accepts(hints);
            assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(type.getMethods()[0])).isNotNull();
        }
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(Item.class.getMethod("detail"))).accepts(hints);
    }

    @Test
    void skipsClientsWithoutOptionalComponents() {
        RuntimeHints hints = hintsFor(PlainClient.class);
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(PlainClient.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ItemsFallback.class)).rejects(hints);
    }

    /** Configurador JSON propio. */
    public static class JsonConfig implements com.juniverse.client.config.JsonMapperConfigurer {
        @Override
        public void configure(tools.jackson.databind.json.JsonMapper.Builder builder) {
            // sin cambios
        }
    }

    /** Cliente con configurador JSON. */
    @RestHttpClient(url = "http://localhost", jsonConfig = JsonConfig.class)
    public interface JsonClient {
        @GetMapping("/ping")
        String ping();
    }

    @Test
    void registersTheJsonConfigurerClassOnlyWhenDeclared() throws NoSuchMethodException {
        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(JsonConfig.class.getDeclaredConstructor()))
                .accepts(hintsFor(JsonClient.class));
        assertThat(RuntimeHintsPredicates.reflection().onType(com.juniverse.client.config.JsonMapperConfigurer.class))
                .rejects(hintsFor(PlainClient.class));
    }

    /** Cliente mínimo: sin interceptores ni fallback. */
    @RestHttpClient(url = "http://localhost")
    public interface PlainClient {
        @GetMapping("/ping")
        String ping();
    }

    @Test
    void theProcessorFindsTheClientsInTheBeanDefinitions() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        BeanDefinition client = BeanDefinitionBuilder.genericBeanDefinition(RestHttpClientFactoryBean.class)
                .addConstructorArgValue(ItemsClient.class)
                .getBeanDefinition();
        factory.registerBeanDefinition("itemsClient", client);
        factory.registerBeanDefinition("other", BeanDefinitionBuilder.genericBeanDefinition(String.class)
                .getBeanDefinition());

        assertThat(new RestHttpClientAotProcessor().processAheadOfTime(factory)).isNotNull();
        assertThat(RestHttpClientAotProcessor.clientInterface(client)).isEqualTo(ItemsClient.class);
        assertThat(RestHttpClientAotProcessor.clientInterface(factory.getBeanDefinition("other"))).isNull();
    }

    @Test
    void theProcessorContributesNothingWithoutClients() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("other", BeanDefinitionBuilder.genericBeanDefinition(String.class)
                .getBeanDefinition());

        assertThat(new RestHttpClientAotProcessor().processAheadOfTime(factory)).isNull();
    }

    @Test
    void theProcessorIsDeclaredInAotFactories() throws IOException {
        try (var in = getClass().getResourceAsStream("/META-INF/spring/aot.factories")) {
            assertThat(new String(in.readAllBytes())).contains(RestHttpClientAotProcessor.class.getName());
        }
    }
}
