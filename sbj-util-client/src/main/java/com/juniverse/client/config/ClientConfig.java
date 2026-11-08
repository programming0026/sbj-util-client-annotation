package com.juniverse.client.config;

import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Beans compartidos por todos los clientes. Se importa mediante {@code @EnableRestHttpClients}.
 *
 * Los nombres de los beans llevan el prefijo {@code restHttpClient} para no chocar con beans de la
 * aplicación (por ejemplo, un {@code sslUtil} propio). {@code RestHttpClientRegistrar} los referencia por
 * estas constantes, así que un bean de la aplicación del mismo tipo tampoco interfiere.
 */
@Configuration(proxyBeanMethods = false)
public class ClientConfig {

    /** Nombre del bean {@link CircuitBreakerUtil}. */
    public static final String CIRCUIT_BREAKER_UTIL_BEAN = "restHttpClientCircuitBreakerUtil";

    /** Nombre del bean {@link RestClientUtil}. */
    public static final String REST_CLIENT_UTIL_BEAN = "restHttpClientRestClientUtil";

    /** Nombre del bean {@link SSLUtil}. */
    public static final String SSL_UTIL_BEAN = "restHttpClientSslUtil";

    /**
     * Fábrica de instancias de Resilience4j.
     *
     * @return la utilidad de Resilience4j
     */
    @Bean(CIRCUIT_BREAKER_UTIL_BEAN)
    public CircuitBreakerUtil restHttpClientCircuitBreakerUtil() {
        return new CircuitBreakerUtil();
    }

    /**
     * Construye los RestClient/RestTemplate de cada cliente.
     *
     * @param sslUtil la utilidad SSL
     * @return la utilidad de clientes HTTP
     */
    @Bean(REST_CLIENT_UTIL_BEAN)
    public RestClientUtil restHttpClientRestClientUtil(@Qualifier(SSL_UTIL_BEAN) SSLUtil sslUtil) {
        return new RestClientUtil(sslUtil);
    }

    /**
     * Crea los contextos SSL; los perfiles activos se leen del {@link Environment} de Spring.
     *
     * @param environment el entorno de Spring
     * @return la utilidad SSL
     */
    @Bean(SSL_UTIL_BEAN)
    public SSLUtil restHttpClientSslUtil(Environment environment) {
        return new SSLUtil(environment);
    }
}
