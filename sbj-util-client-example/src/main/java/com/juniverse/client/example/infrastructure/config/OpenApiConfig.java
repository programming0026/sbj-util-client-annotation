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
 * Documentación OpenAPI de la API del ejemplo.
 *
 * springdoc genera el documento ({@code /v3/api-docs}) y la interfaz ({@code /swagger-ui.html}) a partir de
 * los controladores; aquí solo se definen los datos generales y el esquema de seguridad {@code bearerAuth},
 * que el botón <i>Authorize</i> de Swagger UI usa para enviar el token en {@code Authorization}. Los textos
 * de la documentación están en español; los mensajes de error de la API, en inglés.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Nombre del esquema de seguridad; los controladores lo referencian con {@code @SecurityRequirement}. */
    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * Datos generales de la documentación.
     *
     * @return el documento OpenAPI base
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
