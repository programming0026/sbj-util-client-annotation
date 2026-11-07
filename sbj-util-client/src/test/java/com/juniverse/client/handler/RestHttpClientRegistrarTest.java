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

    @Test
    void wrapsRegistrationErrors() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.setAllowBeanDefinitionOverriding(false);
        AnnotationMetadata metadata = AnnotationMetadata.introspect(DuplicateClients.Config.class);

        assertThatThrownBy(() -> new RestHttpClientRegistrar().registerBeanDefinitions(metadata, registry))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Error registering client");
    }
}
