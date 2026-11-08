package com.juniverse.client.boot;

import com.juniverse.client.handler.RestHttpClientRegistrar;
import io.github.resilience4j.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.type.AnnotationMetadata;

import java.util.List;

/**
 * Registra los clientes escaneando los paquetes de la aplicación Spring Boot (los de la clase con
 * {@code @SpringBootApplication}), igual que Boot hace con los {@code @Component}.
 *
 * Lo usa {@link RestHttpClientAutoConfiguration} cuando la aplicación no declara
 * {@code @EnableRestHttpClients}.
 */
class AutoConfigurationPackagesRegistrar extends RestHttpClientRegistrar {

    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(AutoConfigurationPackagesRegistrar.class);

    /**
     * Registra los clientes de los paquetes de autoconfiguración; si no hay ninguno registrado, no hace nada.
     *
     * @param metadata los metadatos de la autoconfiguración (no se usan)
     * @param registry el registro de definiciones de bean
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
