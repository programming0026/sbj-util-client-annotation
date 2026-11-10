package io.github.programming0026.juniverse.client.boot;

import io.github.programming0026.juniverse.client.config.ClientConfig;
import io.github.programming0026.juniverse.client.handler.RestHttpClientRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Autoconfiguración de Spring Boot: registra los clientes {@code @RestHttpClient} de los paquetes de la
 * aplicación sin necesidad de {@code @EnableRestHttpClients}.
 *
 * No se activa si la aplicación declara {@code @EnableRestHttpClients} (entonces mandan sus
 * {@code basePackages}) ni con {@code rest-http-client.auto-scan.enabled=false}.
 *
 * @author Java Universe
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "rest-http-client.auto-scan", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnMissingBean(name = RestHttpClientRegistrar.ENABLED_MARKER_BEAN)
@Import({AutoConfigurationPackagesRegistrar.class, ClientConfig.class})
public class RestHttpClientAutoConfiguration {
}
