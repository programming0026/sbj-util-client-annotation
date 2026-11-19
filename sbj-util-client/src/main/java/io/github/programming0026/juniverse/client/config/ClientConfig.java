package io.github.programming0026.juniverse.client.config;

import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import io.github.programming0026.juniverse.client.util.RestClientUtil;
import io.github.programming0026.juniverse.client.util.SSLUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Beans shared by all the clients. It is imported through {@code @EnableRestHttpClients}.
 *
 * The bean names carry the {@code restHttpClient} prefix so they do not clash with application beans (for
 * example, an application-defined {@code sslUtil}). {@code RestHttpClientRegistrar} references them by
 * these constants, so an application bean of the same type does not interfere either.
 *
 * @author Java Universe
 */
@Configuration(proxyBeanMethods = false)
public class ClientConfig {

    /** Name of the {@link CircuitBreakerUtil} bean. */
    public static final String CIRCUIT_BREAKER_UTIL_BEAN = "restHttpClientCircuitBreakerUtil";

    /** Name of the {@link RestClientUtil} bean. */
    public static final String REST_CLIENT_UTIL_BEAN = "restHttpClientRestClientUtil";

    /** Name of the {@link SSLUtil} bean. */
    public static final String SSL_UTIL_BEAN = "restHttpClientSslUtil";

    /**
     * Factory of Resilience4j instances.
     *
     * @return the Resilience4j utility
     */
    @Bean(CIRCUIT_BREAKER_UTIL_BEAN)
    public CircuitBreakerUtil restHttpClientCircuitBreakerUtil() {
        return new CircuitBreakerUtil();
    }

    /**
     * Builds the RestClient/RestTemplate of each client.
     *
     * @param sslUtil the SSL utility
     * @return the HTTP client utility
     */
    @Bean(REST_CLIENT_UTIL_BEAN)
    public RestClientUtil restHttpClientRestClientUtil(@Qualifier(SSL_UTIL_BEAN) SSLUtil sslUtil) {
        return new RestClientUtil(sslUtil);
    }

    /**
     * Creates the SSL contexts; the active profiles are read from the Spring {@link Environment}.
     *
     * @param environment the Spring environment
     * @return the SSL utility
     */
    @Bean(SSL_UTIL_BEAN)
    public SSLUtil restHttpClientSslUtil(Environment environment) {
        return new SSLUtil(environment);
    }
}
