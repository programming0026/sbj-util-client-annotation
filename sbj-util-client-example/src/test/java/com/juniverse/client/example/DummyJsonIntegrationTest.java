package com.juniverse.client.example;

import com.juniverse.client.example.support.FakeDummyJsonServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de punta a punta: la aplicación completa (API REST → casos de uso → adaptadores → clientes
 * generados por {@code sbj-util-client}) contra un DummyJSON falso local. Al arrancar el contexto, la
 * librería valida y crea los tres clientes, así que cualquier error de declaración rompe esta clase.
 *
 * @author Java Universe
 */
@SpringBootTest
class DummyJsonIntegrationTest {

    private static final FakeDummyJsonServer DUMMY_JSON = new FakeDummyJsonServer();

    private static final String PRODUCT_JSON = """
            {"id":1,"title":"Essence Mascara","description":"Lash","category":"beauty","price":9.99,
             "discountPercentage":10.48,"rating":4.94,"stock":5,"tags":["beauty"],"brand":"Essence",
             "sku":"BEA-001","thumbnail":"https://cdn/t.webp","images":["https://cdn/1.webp"],
             "reviews":[{"rating":2,"comment":"ignored"}],"meta":{"barcode":"x"}}""";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("dummyjson.base-url", DUMMY_JSON::baseUrl);
    }

    @AfterAll
    static void stopServer() {
        DUMMY_JSON.stop();
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DUMMY_JSON.reset();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ------------------------------------------------------------------ productos

    @Test
    void listsProductsAndIgnoresUnknownFields() throws Exception {
        DUMMY_JSON.replyJson("GET", "/products", 200,
                "{\"products\":[" + PRODUCT_JSON + "],\"total\":194,\"skip\":0,\"limit\":30}");

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Essence Mascara"))
                .andExpect(jsonPath("$.items[0].price").value(9.99))
                .andExpect(jsonPath("$.total").value(194))
                .andExpect(jsonPath("$.hasNext").value(true));

        assertThat(DUMMY_JSON.requests("GET", "/products")).singleElement()
                .satisfies(request -> assertThat(request.query()).isEqualTo("limit=30&skip=0"));
    }

    @Test
    void searchSortsAndPaginatesThroughTheSearchEndpoint() throws Exception {
        DUMMY_JSON.replyJson("GET", "/products/search", 200,
                "{\"products\":[],\"total\":0,\"skip\":10,\"limit\":5}");

        mvc.perform(get("/api/products?q=phone case&limit=5&skip=10&sortBy=price&order=desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        assertThat(DUMMY_JSON.requests("GET", "/products/search")).singleElement()
                .satisfies(request -> assertThat(request.query())
                        .isEqualTo("q=phone%20case&limit=5&skip=10&sortBy=price&order=desc"));
    }

    @Test
    void filtersByCategory() throws Exception {
        DUMMY_JSON.replyJson("GET", "/products/category/smartphones", 200,
                "{\"products\":[" + PRODUCT_JSON + "],\"total\":1,\"skip\":0,\"limit\":30}");

        mvc.perform(get("/api/products?category=smartphones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listsCategories() throws Exception {
        DUMMY_JSON.replyJson("GET", "/products/categories", 200,
                "[{\"slug\":\"beauty\",\"name\":\"Beauty\",\"url\":\"https://x/beauty\"}]");

        mvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("beauty"))
                .andExpect(jsonPath("$[0].name").value("Beauty"));
    }

    @Test
    void getsOneProductAndMapsMissingOnesTo404() throws Exception {
        DUMMY_JSON.replyJson("GET", "/products/1", 200, PRODUCT_JSON);
        DUMMY_JSON.replyJson("GET", "/products/99999", 404, "{\"message\":\"Product with id '99999' not found\"}");

        mvc.perform(get("/api/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.brand").value("Essence"));
        mvc.perform(get("/api/products/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product with id 99999 not found"));
    }

    @Test
    void createsAProductSendingOnlyTheProvidedFields() throws Exception {
        DUMMY_JSON.replyJson("POST", "/products/add", 201, "{\"id\":195,\"title\":\"Lamp\"}");

        mvc.perform(post("/api/products").contentType(APPLICATION_JSON).content("{\"title\":\"Lamp\",\"price\":12.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(195))
                .andExpect(jsonPath("$.title").value("Lamp"));

        assertThat(DUMMY_JSON.requests("POST", "/products/add")).singleElement().satisfies(request -> {
            assertThat(request.headers().get("content-type")).startsWith("application/json");
            assertThat(request.body()).contains("\"title\":\"Lamp\"").contains("\"price\":12.5")
                    .doesNotContain("description").doesNotContain("brand");
        });
    }

    @Test
    void updatesAndDeletesProducts() throws Exception {
        DUMMY_JSON.replyJson("PATCH", "/products/1", 200, "{\"id\":1,\"title\":\"Renamed\"}");
        DUMMY_JSON.replyJson("DELETE", "/products/1", 200, "{\"id\":1,\"title\":\"Deleted\",\"isDeleted\":true}");
        DUMMY_JSON.replyJson("PATCH", "/products/404", 404, "{\"message\":\"not found\"}");

        mvc.perform(patch("/api/products/1").contentType(APPLICATION_JSON).content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed"));
        mvc.perform(delete("/api/products/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Deleted"));
        mvc.perform(patch("/api/products/404").contentType(APPLICATION_JSON).content("{\"stock\":3}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidInputIsRejectedByTheDomainWithoutCallingTheService() throws Exception {
        mvc.perform(get("/api/products?limit=1000")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 100"));
        mvc.perform(get("/api/products?q=a&category=b")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?sortBy=color")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/products").contentType(APPLICATION_JSON).content("{\"price\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("title is required"));
        mvc.perform(patch("/api/products/1").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(DUMMY_JSON.requests()).isEmpty();
    }

    @Test
    void retriesTransientFailuresOfIdempotentRequestsThenReportsServiceUnavailable() throws Exception {
        DUMMY_JSON.replyJson("GET", "/products/2", 503, "{\"message\":\"down\"}");

        mvc.perform(get("/api/products/2"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("DummyJSON is unavailable while trying to get product 2"));

        // GET es idempotente: 3 intentos (maxRetries = 3).
        assertThat(DUMMY_JSON.requests("GET", "/products/2")).hasSize(3);
    }

    @Test
    void doesNotRetryAServerErrorOnANonIdempotentCreate() throws Exception {
        DUMMY_JSON.replyJson("POST", "/products/add", 503, "{\"message\":\"down\"}");

        mvc.perform(post("/api/products").contentType(APPLICATION_JSON).content("{\"title\":\"Lamp\",\"price\":1}"))
                .andExpect(status().isServiceUnavailable());

        // POST no es idempotente: un solo intento, para no crear el producto dos veces.
        assertThat(DUMMY_JSON.requests("POST", "/products/add")).hasSize(1);
    }

    // ------------------------------------------------------------------ autenticación

    @Test
    void logsIn() throws Exception {
        DUMMY_JSON.replyJson("POST", "/auth/login", 200, """
                {"id":1,"username":"emilys","email":"e@x.com","firstName":"Emily","lastName":"Johnson",
                 "gender":"female","image":"https://i/1","accessToken":"access-1","refreshToken":"refresh-1"}""");

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"emilys\",\"password\":\"emilyspass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("emilys"))
                .andExpect(jsonPath("$.tokens.accessToken").value("access-1"))
                .andExpect(jsonPath("$.tokens.refreshToken").value("refresh-1"));

        assertThat(DUMMY_JSON.requests("POST", "/auth/login")).singleElement()
                .satisfies(request -> assertThat(request.body()).contains("\"username\":\"emilys\"")
                        .contains("\"password\":\"emilyspass\"").doesNotContain("expiresInMins"));
    }

    @Test
    void mapsRejectedCredentialsTo401() throws Exception {
        DUMMY_JSON.replyJson("POST", "/auth/login", 400, "{\"message\":\"Invalid credentials\"}");

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        assertThat(DUMMY_JSON.requests("POST", "/auth/login")).hasSize(1);
    }

    @Test
    void getsTheCurrentUserSendingTheBearerToken() throws Exception {
        DUMMY_JSON.replyJson("GET", "/auth/me", 200,
                "{\"id\":1,\"username\":\"emilys\",\"email\":\"e@x.com\",\"firstName\":\"Emily\","
                        + "\"lastName\":\"Johnson\",\"gender\":\"female\",\"image\":\"i\",\"age\":28}");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer access-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("emilys"));

        assertThat(DUMMY_JSON.requests("GET", "/auth/me")).singleElement()
                .satisfies(request -> assertThat(request.headers().get("authorization")).isEqualTo("Bearer access-1"));
    }

    @Test
    void rejectsMissingOrInvalidAccessTokens() throws Exception {
        DUMMY_JSON.replyJson("GET", "/auth/me", 401, "{\"message\":\"Invalid/Expired Token!\"}");

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        assertThat(DUMMY_JSON.requests()).as("no token, no call").isEmpty();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired"));
    }

    @Test
    void refreshesTokensAndRejectsInvalidRefreshTokens() throws Exception {
        DUMMY_JSON.replyJson("POST", "/auth/refresh", 200, "{\"accessToken\":\"a2\",\"refreshToken\":\"r2\"}");

        mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON).content("{\"refreshToken\":\"r1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a2"));

        DUMMY_JSON.replyJson("POST", "/auth/refresh", 403, "{\"message\":\"Invalid refresh token\"}");
        mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON).content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ imágenes

    @Test
    void generatesAnImageWithTheRequestedOptions() throws Exception {
        byte[] webp = {'R', 'I', 'F', 'F', 1, 2, 3};
        DUMMY_JSON.replyBytes("GET", "/image/400x200/008080/ffffff", "image/webp", webp);

        mvc.perform(get("/api/images?width=400&height=200&background=008080&color=FFFFFF&text=Hello World"
                        + "&fontSize=24&format=webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(webp));

        assertThat(DUMMY_JSON.requests("GET", "/image/400x200/008080/ffffff")).singleElement()
                .satisfies(request -> assertThat(request.query())
                        .isEqualTo("text=Hello%20World&fontSize=24&type=webp"));
    }

    @Test
    void usesDefaultsAndRejectsInvalidImagesWithoutCallingTheService() throws Exception {
        DUMMY_JSON.replyBytes("GET", "/image/300x200/cccccc/969696", "image/png", new byte[]{1});

        mvc.perform(get("/api/images")).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        DUMMY_JSON.reset();

        mvc.perform(get("/api/images?width=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/images?background=red")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/images?format=gif")).andExpect(status().isBadRequest());
        assertThat(DUMMY_JSON.requests()).isEmpty();
    }
}
