package io.github.programming0026.juniverse.client.boot;

import io.github.programming0026.juniverse.client.handler.RestHttpClientRegistrar;
import io.github.resilience4j.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.type.AnnotationMetadata;

import java.util.List;

/**
 * Registers the clients by scanning the packages of the Spring Boot application (those of the class with
 * {@code @SpringBootApplication}), just as Boot does with {@code @Component}.
 *
 * It is used by {@link RestHttpClientAutoConfiguration} when the application does not declare
 * {@code @EnableRestHttpClients}.
 *
 * @author Java Universe
 */
class AutoConfigurationPackagesRegistrar extends RestHttpClientRegistrar {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(AutoConfigurationPackagesRegistrar.class);

    /**
     * Registers the clients of the auto-configuration packages; if none is registered, it does nothing.
     *
     * @param metadata the auto-configuration metadata (not used)
     * @param registry the bean definition registry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
        if (!(registry instanceof BeanFactory beanFactory) || !AutoConfigurationPackages.has(beanFactory)) {
            LOG.debug("No auto-configuration packages found; skipping RestHttpClient auto-scan");
            return;
        }
        List<String> packages = AutoConfigurationPackages.get(beanFactory);
        LOG.debug("Auto-scanning RestHttpClient interfaces in: {}", packages);
        registerClients(packages.toArray(String[]::new), registry);
    }
}
