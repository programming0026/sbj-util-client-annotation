package com.juniverse.client.config;

import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientConfig {

    /**
     * The configuration of Resilience 4j.
     *
     * @return The object
     */
    @Bean
    public CircuitBreakerUtil resilience4jConfiguration() {
        return new CircuitBreakerUtil();
    }

    @Bean
    public RestClientUtil httpClientConfigurer() {
        return new RestClientUtil(new SSLUtil());
    }

    @Bean
    public SSLUtil sslUtil() {
        return new SSLUtil();
    }
}
