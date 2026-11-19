package io.github.programming0026.juniverse.client.boot;

import io.github.programming0026.juniverse.client.config.ClientConfig;
import io.github.programming0026.juniverse.client.handler.RestHttpClientRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration: registers the {@code @RestHttpClient} clients found in the application's
 * packages without needing {@code @EnableRestHttpClients}.
 *
 * It is not activated if the application declares {@code @EnableRestHttpClients} (then its
 * {@code basePackages} take precedence) nor with {@code rest-http-client.auto-scan.enabled=false}.
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
