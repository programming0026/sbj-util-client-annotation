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
 * of the documentation and the API error messages are in English.
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
                                Example API built with the declarative REST clients of sbj-util-client. \
                                Each endpoint delegates to a hexagonal use case that calls DummyJSON \
                                (products, authentication and images).

                                To try the authenticated endpoint: call **POST /api/auth/login** with \
                                `emilys` / `emilyspass`, click **Authorize** and paste the access token.
                                DummyJSON simulates creating, modifying and deleting products: it stores nothing.""")
                        .license(new License().name("MIT License").url("https://opensource.org/license/mit")))
                .externalDocs(new ExternalDocumentation()
                        .description("DummyJSON documentation")
                        .url("https://dummyjson.com/docs"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token returned by POST /api/auth/login")));
    }
}
