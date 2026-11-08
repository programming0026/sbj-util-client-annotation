package com.juniverse.client.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La documentación OpenAPI se genera a partir de los controladores: esta prueba comprueba que describe la
 * API completa y que Swagger UI está disponible. No llama a DummyJSON.
 */
@SpringBootTest
class OpenApiDocumentationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void describesTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(containsString("3.")))
                .andExpect(jsonPath("$.info.title").value("sbj-util-client-example"))
                .andExpect(jsonPath("$.info.license.name").value("MIT License"))
                .andExpect(jsonPath("$.tags[*].name").value(containsInAnyOrder("Productos", "Autenticación", "Imágenes")));
    }

    @Test
    void documentsEveryEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/products'].get.summary").value("Listar, buscar o filtrar productos"))
                .andExpect(jsonPath("$.paths['/api/products'].post.summary").value("Crear un producto"))
                .andExpect(jsonPath("$.paths['/api/products/categories'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/images'].get").exists());
    }

    @Test
    void documentsQueryParametersWithTheirDescriptions() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[*].name")
                        .value(containsInAnyOrder("limit", "skip", "q", "category", "sortBy", "order")))
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[?(@.name=='limit')].description")
                        .value(hasItem("Tamaño de página (de 1 a 100)")))
                .andExpect(jsonPath("$.paths['/api/images'].get.parameters[*].name")
                        .value(hasItem("fontSize")));
    }

    @Test
    void documentsImageContentTypesAndErrorResponses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/images'].get.responses['200'].content['image/png']").exists())
                .andExpect(jsonPath("$.paths['/api/images'].get.responses['200'].content['image/jpeg']").exists())
                .andExpect(jsonPath("$.paths['/api/images'].get.responses['200'].content['image/webp']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.responses['503']").exists());
    }

    @Test
    void documentsTheBearerSecurityScheme() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                // Solo /me exige el token, y el header Authorization no aparece como parámetro suelto.
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist());
    }

    @Test
    void documentsRequestBodiesWithExamples() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.username.example").value("emilys"))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.format").value("password"))
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.title.example")
                        .value("Lámpara de escritorio"))
                .andExpect(jsonPath("$.components.schemas.ProductResponse.properties.title").exists());
    }

    @Test
    void doesNotLeakInternalTypes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(content().string(not(containsString("DummyJson"))))
                .andExpect(content().string(not(containsString("ProductDto"))));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
    }
}
