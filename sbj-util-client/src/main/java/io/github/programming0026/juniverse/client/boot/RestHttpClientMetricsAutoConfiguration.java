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
 * Spring Boot auto-configuration: publishes to Micrometer the Resilience4j metrics of all the
 * clients (circuit breaker, retry, rate limiter and time limiter), with the client name as a tag.
 *
 * It is only activated if {@code micrometer-core} and {@code resilience4j-micrometer} are on the classpath and
 * clients exist. It exposes a {@link MeterBinder}, which Spring Boot applies to its {@code MeterRegistry}
 * instances; clients created after the binding are also published.
 *
 * @author Java Universe
 */
@AutoConfiguration(after = RestHttpClientAutoConfiguration.class)
@ConditionalOnClass(name = {"io.micrometer.core.instrument.binder.MeterBinder",
        "io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics"})
@ConditionalOnBean(name = ClientConfig.CIRCUIT_BREAKER_UTIL_BEAN)
public class RestHttpClientMetricsAutoConfiguration {

    /**
     * Binds the clients' Resilience4j registries to Micrometer.
     *
     * @param circuitBreakerUtil the factory of the clients' Resilience4j instances
     * @return the metrics binder
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
