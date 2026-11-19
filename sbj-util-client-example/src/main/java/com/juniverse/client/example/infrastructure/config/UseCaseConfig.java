package com.juniverse.client.example.infrastructure.config;

import com.juniverse.client.example.application.port.out.AuthenticationGateway;
import com.juniverse.client.example.application.port.out.ImageGenerator;
import com.juniverse.client.example.application.port.out.ProductReader;
import com.juniverse.client.example.application.port.out.ProductWriter;
import com.juniverse.client.example.application.service.AuthenticationService;
import com.juniverse.client.example.application.service.ImageService;
import com.juniverse.client.example.application.service.ProductCommandService;
import com.juniverse.client.example.application.service.ProductQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the use cases with their output ports.
 *
 * The application services carry no Spring annotations: here, in the infrastructure, is where it is
 * decided which adapter implements each port. Replacing DummyJSON with another provider only requires a new
 * adapter; this configuration and the rest of the application do not change.
 *
 * @author Java Universe
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfig {

    /**
     * Catalog query use cases.
     *
     * @param productReader the reading port
     * @return the service
     */
    @Bean
    public ProductQueryService productQueryService(ProductReader productReader) {
        return new ProductQueryService(productReader);
    }

    /**
     * Use cases that modify the catalog.
     *
     * @param productWriter the writing port
     * @return the service
     */
    @Bean
    public ProductCommandService productCommandService(ProductWriter productWriter) {
        return new ProductCommandService(productWriter);
    }

    /**
     * Authentication use cases.
     *
     * @param authenticationGateway the identity provider port
     * @return the service
     */
    @Bean
    public AuthenticationService authenticationService(AuthenticationGateway authenticationGateway) {
        return new AuthenticationService(authenticationGateway);
    }

    /**
     * Image generation use case.
     *
     * @param imageGenerator the image generator port
     * @return the service
     */
    @Bean
    public ImageService imageService(ImageGenerator imageGenerator) {
        return new ImageService(imageGenerator);
    }
}
