package com.juniverse.client.config;

import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans compartidos por todos los clientes. Se importa mediante {@code @EnableRestHttpClients}.
 */
@Configuration
public class ClientConfig {

    /**
     * Fábrica de instancias de Resilience4j. {@code RestHttpClientRegistrar} la referencia por el nombre
     * {@code resilience4jConfiguration}, así que no se debe renombrar.
     *
     * @return la utilidad de Resilience4j
     */
    @Bean
    public CircuitBreakerUtil resilience4jConfiguration() {
        return new CircuitBreakerUtil();
    }

    /**
     * Construye los RestClient/RestTemplate de cada cliente.
     *
     * @return la utilidad de clientes HTTP
     */
    @Bean
    public RestClientUtil httpClientConfigurer() {
        return new RestClientUtil(sslUtil());
    }

    /**
     * Crea los contextos SSL.
     *
     * @return la utilidad SSL
     */
    @Bean
    public SSLUtil sslUtil() {
        return new SSLUtil();
    }
}
