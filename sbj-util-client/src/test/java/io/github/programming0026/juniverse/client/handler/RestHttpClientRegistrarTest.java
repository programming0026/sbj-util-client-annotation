package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.util.Constants;
import io.github.programming0026.juniverse.client.autoconfigure.EnableRestHttpClients;
import io.github.programming0026.juniverse.client.config.ClientConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.handler.duplicate.DuplicateClients;
import io.github.programming0026.juniverse.client.handler.scan.ScanConfig;
import io.github.programming0026.juniverse.client.handler.scan.ScannedClients;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.SSLUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotationMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestHttpClientRegistrarTest {

    @Configuration
    @EnableRestHttpClients(basePackages = Constants.BASE + ".handler.scan")
    static class ExplicitPackagesConfig {
    }

    @Test
    void registersAnnotatedInterfacesFromOwnPackageByDefault() {
        try (var context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            assertThat(context.getBean("namedClient")).isInstanceOf(ScannedClients.NamedClient.class);
            assertThat(context.getBean("derivedNameClient")).isInstanceOf(ScannedClients.DerivedNameClient.class);
            assertThat(context.getBeanNamesForType(ScannedClients.NotAClient.class)).isEmpty();
        }
    }

    @Test
    void registersInterfacesFromExplicitBasePackagesAndSharedBeans() {
        try (var context = new AnnotationConfigApplicationContext(ExplicitPackagesConfig.class)) {
            assertThat(context.getBean(ScannedClients.NamedClient.class)).isNotNull();
            assertThat(context.getBean(ClientConfig.CIRCUIT_BREAKER_UTIL_BEAN)).isInstanceOf(CircuitBreakerUtil.class);
            assertThat(context.getBean(ClientConfig.REST_CLIENT_UTIL_BEAN)).isInstanceOf(RestClientUtil.class);
            assertThat(context.getBean(ClientConfig.SSL_UTIL_BEAN)).isInstanceOf(SSLUtil.class);
            assertThat(context.getBean(RestClientUtil.class)).isNotNull();
            assertThat(context.getBean(SSLUtil.class)).isNotNull();
            assertThat(context.getBean(ClientConfig.class)).isNotNull();
        }
    }

    @Configuration
    @EnableRestHttpClients(basePackages = Constants.BASE + ".handler.scan")
    static class OverlappingConfig {
    }

    @Configuration
    static class RenamedResilienceConfig {

        @Bean
        CircuitBreakerUtil customResilience() {
            return new CircuitBreakerUtil();
        }

        @Bean
        SSLUtil customSslUtil() {
            return new SSLUtil();
        }

        @Bean
        RestClientUtil customRestClientUtil() {
            return new RestClientUtil(new SSLUtil());
        }
    }

    @Test
    void duplicateBeanNamesAreRejectedEvenWhenOverridingIsAllowed() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.setAllowBeanDefinitionOverriding(true);
        AnnotationMetadata metadata = AnnotationMetadata.introspect(DuplicateClients.Config.class);

        assertThatThrownBy(() -> new RestHttpClientRegistrar().registerBeanDefinitions(metadata, registry))
                .isInstanceOf(ClientException.class)
                .hasMessageStartingWith("Duplicate bean name 'dup' for client")
                .hasMessageContaining("@RestHttpClient(name = ...)");
    }

    @Test
    void overlappingScansRegisterEachClientOnce() {
        try (var context = new AnnotationConfigApplicationContext(ExplicitPackagesConfig.class,
                OverlappingConfig.class)) {
            assertThat(context.getBeanNamesForType(ScannedClients.NamedClient.class)).containsExactly("namedClient");
        }
    }

    @Test
    void clientTypeIsKnownWithoutInstantiatingTheFactoryBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ExplicitPackagesConfig.class);
            context.refresh();

            assertThat(context.getBeanFactory().getBeanNamesForType(ScannedClients.NamedClient.class, true, false))
                    .containsExactly("namedClient");
        }
    }

    @Test
    void applicationBeansWithSameTypeOrGenericNamesDoNotInterfere() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ExplicitPackagesConfig.class, RenamedResilienceConfig.class);
            context.registerBean("sslUtil", String.class, () -> "application bean");
            context.registerBean("httpClientConfigurer", String.class, () -> "application bean");
            context.registerBean("resilience4jConfiguration", String.class, () -> "application bean");
            context.refresh();

            assertThat(context.getBean(ScannedClients.NamedClient.class)).isNotNull();
            assertThat(context.getBean("sslUtil")).isEqualTo("application bean");
            assertThat(context.getBeansOfType(CircuitBreakerUtil.class)).hasSize(2);
            assertThat(context.getBeansOfType(SSLUtil.class)).hasSize(2);
        }
    }
}
