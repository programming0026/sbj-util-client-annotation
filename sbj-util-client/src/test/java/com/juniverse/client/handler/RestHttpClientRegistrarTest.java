package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import com.juniverse.client.config.ClientConfig;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.handler.duplicate.DuplicateClients;
import com.juniverse.client.handler.scan.ScanConfig;
import com.juniverse.client.handler.scan.ScannedClients;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
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
    @EnableRestHttpClients(basePackages = "com.juniverse.client.handler.scan")
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
            assertThat(context.getBean("resilience4jConfiguration")).isInstanceOf(CircuitBreakerUtil.class);
            assertThat(context.getBean(RestClientUtil.class)).isNotNull();
            assertThat(context.getBean(SSLUtil.class)).isNotNull();
            assertThat(context.getBean(ClientConfig.class)).isNotNull();
        }
    }

    @Configuration
    @EnableRestHttpClients(basePackages = "com.juniverse.client.handler.scan")
    static class OverlappingConfig {
    }

    @Configuration
    static class RenamedResilienceConfig {

        @Bean
        CircuitBreakerUtil customResilience() {
            return new CircuitBreakerUtil();
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
    void dependenciesAreResolvedByTypeNotByBeanName() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ExplicitPackagesConfig.class, RenamedResilienceConfig.class);
            // Quita el bean con el nombre que antes era obligatorio: debe usarse el otro por tipo.
            context.registerBean("resilience4jConfiguration", String.class, () -> "not a CircuitBreakerUtil");
            context.refresh();

            assertThat(context.getBean(ScannedClients.NamedClient.class)).isNotNull();
        }
    }
}
