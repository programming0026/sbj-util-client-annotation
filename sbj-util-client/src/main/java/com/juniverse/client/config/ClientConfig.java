package com.juniverse.client.config;

import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Beans compartidos por todos los clientes. Se importa mediante {@code @EnableRestHttpClients}.
 */
@Configuration
public class ClientConfig {

    /**
     * Fábrica de instancias de Resilience4j. Los factory beans de los clientes la reciben por tipo.
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
     * @param sslUtil la utilidad SSL
     * @return la utilidad de clientes HTTP
     */
    @Bean
    public RestClientUtil httpClientConfigurer(SSLUtil sslUtil) {
        return new RestClientUtil(sslUtil);
    }

    /**
     * Crea los contextos SSL; los perfiles activos se leen del {@link Environment} de Spring.
     *
     * @param environment el entorno de Spring
     * @return la utilidad SSL
     */
    @Bean
    public SSLUtil sslUtil(Environment environment) {
        return new SSLUtil(environment);
    }
}
