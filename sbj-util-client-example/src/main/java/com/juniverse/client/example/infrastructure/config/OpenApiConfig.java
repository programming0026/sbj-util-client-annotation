package com.juniverse.client.example.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation of the example's API.
 *
 * springdoc generates the document ({@code /v3/api-docs}) and the UI ({@code /swagger-ui.html}) from
 * the controllers; here only the general data and the {@code bearerAuth} security scheme are defined,
 * which Swagger UI's <i>Authorize</i> button uses to send the token in {@code Authorization}. The texts
 * of the documentation are in Spanish; the API error messages, in English.
 *
 * @author Java Universe
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Name of the security scheme; the controllers reference it with {@code @SecurityRequirement}. */
    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * General data of the documentation.
     *
     * @return the base OpenAPI document
     */
    @Bean
    public OpenAPI exampleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("sbj-util-client-example")
                        .version("1.0.0")
                        .description("""
                                API de ejemplo construida con los clientes REST declarativos de sbj-util-client. \
                                Cada endpoint delega en un caso de uso hexagonal que llama a DummyJSON \
                                (productos, autenticación e imágenes).

                                Para probar el endpoint autenticado: llama a **POST /api/auth/login** con \
                                `emilys` / `emilyspass`, pulsa **Authorize** y pega el token de acceso.
                                DummyJSON simula crear, modificar y eliminar productos: no guarda nada.""")
                        .license(new License().name("MIT License").url("https://opensource.org/license/mit")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentación de DummyJSON")
                        .url("https://dummyjson.com/docs"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token de acceso que devuelve POST /api/auth/login")));
    }
}
