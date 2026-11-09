package io.github.programming0026.juniverse.client.boot;

import io.github.programming0026.juniverse.client.config.ClientConfig;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguración de Spring Boot: publica en Micrometer las métricas de Resilience4j de todos los
 * clientes (circuit breaker, retry, rate limiter y time limiter), con el nombre del cliente como tag.
 *
 * Se activa solo si {@code micrometer-core} y {@code resilience4j-micrometer} están en el classpath y existen
 * clientes. Expone un {@link MeterBinder}, que Spring Boot aplica a sus {@code MeterRegistry}; los clientes
 * creados después del enlace también se publican.
 */
@AutoConfiguration(after = RestHttpClientAutoConfiguration.class)
@ConditionalOnClass(name = {"io.micrometer.core.instrument.binder.MeterBinder",
        "io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics"})
@ConditionalOnBean(name = ClientConfig.CIRCUIT_BREAKER_UTIL_BEAN)
public class RestHttpClientMetricsAutoConfiguration {

    /**
     * Enlaza los registros de Resilience4j de los clientes con Micrometer.
     *
     * @param circuitBreakerUtil la fábrica de instancias de Resilience4j de los clientes
     * @return el binder de métricas
     */
    @Bean
    public MeterBinder restHttpClientResilienceMetrics(
            @Qualifier(ClientConfig.CIRCUIT_BREAKER_UTIL_BEAN) CircuitBreakerUtil circuitBreakerUtil) {
        return registry -> {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerUtil.getCircuitBreakerRegistry())
                    .bindTo(registry);
            TaggedRetryMetrics.ofRetryRegistry(circuitBreakerUtil.getRetryRegistry()).bindTo(registry);
            TaggedRateLimiterMetrics.ofRateLimiterRegistry(circuitBreakerUtil.getRateLimiterRegistry())
                    .bindTo(registry);
            TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(circuitBreakerUtil.getTimeLimiterRegistry())
                    .bindTo(registry);
        };
    }
}
