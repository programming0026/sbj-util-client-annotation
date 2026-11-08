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
 * Ensambla los casos de uso con sus puertos de salida.
 *
 * Los servicios de la aplicación no llevan anotaciones de Spring: aquí, en la infraestructura, es donde se
 * decide qué adaptador implementa cada puerto. Cambiar DummyJSON por otro proveedor solo exige un adaptador
 * nuevo; esta configuración y el resto de la aplicación no cambian.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfig {

    /**
     * Casos de uso de consulta del catálogo.
     *
     * @param productReader el puerto de lectura
     * @return el servicio
     */
    @Bean
    public ProductQueryService productQueryService(ProductReader productReader) {
        return new ProductQueryService(productReader);
    }

    /**
     * Casos de uso que modifican el catálogo.
     *
     * @param productWriter el puerto de escritura
     * @return el servicio
     */
    @Bean
    public ProductCommandService productCommandService(ProductWriter productWriter) {
        return new ProductCommandService(productWriter);
    }

    /**
     * Casos de uso de autenticación.
     *
     * @param authenticationGateway el puerto del proveedor de identidad
     * @return el servicio
     */
    @Bean
    public AuthenticationService authenticationService(AuthenticationGateway authenticationGateway) {
        return new AuthenticationService(authenticationGateway);
    }

    /**
     * Caso de uso de generación de imágenes.
     *
     * @param imageGenerator el puerto del generador de imágenes
     * @return el servicio
     */
    @Bean
    public ImageService imageService(ImageGenerator imageGenerator) {
        return new ImageService(imageGenerator);
    }
}
