package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.util.CircuitBreakerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Argumentos de tipo Map en {@link RestHttpClientInvocationHandler}. */
class RestHttpClientInvocationHandlerMapTest {

    private static final String BASE = "http://localhost/api";

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface MapClient {

        @GetMapping("/items")
        String search(@RequestParam("fixed") String fixed, @RequestParam Map<String, Object> filters);

        @GetMapping("/items")
        String searchMulti(@RequestParam MultiValueMap<String, String> filters);

        @GetMapping("/items")
        String searchOptional(@RequestParam(required = false) Map<String, String> filters);

        @GetMapping(path = "/items", headers = "X-Role=default")
        String withHeaders(@RequestHeader Map<String, Object> headers);

        @GetMapping("/orgs/{org}/items/{id}")
        String byPath(@PathVariable Map<String, Object> variables);
    }

    private static MapClient proxy(Object httpClient) {
        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(httpClient,
                MapClient.class.getAnnotation(RestHttpClient.class), new DefaultErrorHandler(),
                MapClient.class, new CircuitBreakerUtil());
        return (MapClient) Proxy.newProxyInstance(MapClient.class.getClassLoader(),
                new Class<?>[]{MapClient.class}, handler);
    }

    @Test
    void mapEntriesBecomeQueryParams() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items?fixed=1&q=a%26b&tag=x&tag=y&page=2"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("q", "a&b");
        filters.put("tag", List.of("x", "y"));
        filters.put("skipped", null);
        filters.put("empty", List.of());
        filters.put(null, "no-key");
        filters.put("page", 2);

        assertThat(proxy(template).search("1", filters)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void multiValueMapProducesRepeatedParams() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items?status=new&status=old"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        MultiValueMap<String, String> filters = new LinkedMultiValueMap<>();
        filters.add("status", "new");
        filters.add("status", "old");

        assertThat(proxy(template).searchMulti(filters)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void emptyOrOptionalNullMapsSendNothing() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/items")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        MapClient client = proxy(template);

        assertThat(client.searchMulti(new LinkedMultiValueMap<>())).isEqualTo("ok");
        assertThat(client.searchOptional(null)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void nullRequiredMapIsRejected() {
        MapClient client = proxy(new RestTemplate());

        assertThatThrownBy(() -> client.searchMulti(null))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required request parameter map 'filters' is null or empty in MapClient.searchMulti");
        assertThatThrownBy(() -> client.withHeaders(null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("request header map 'headers'");
        assertThatThrownBy(() -> client.byPath(null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("path variable map 'variables'");
    }

    @Test
    void mapEntriesBecomeHeadersOverridingMappingHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/items"))
                .andExpect(header("X-Role", "admin", "user"))
                .andExpect(header("X-Tenant", "acme"))
                .andExpect(headerDoesNotExist("X-Null"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        Map<String, Object> headers = new HashMap<>();
        headers.put("x-role", List.of("admin", "user"));
        headers.put("X-Tenant", "acme");
        headers.put("X-Null", null);

        assertThat(proxy(builder.build()).withHeaders(headers)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void mapEntriesBecomePathVariables() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/orgs/my%20org/items/1%2C2"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(proxy(template).byPath(Map.of("org", "my org", "id", List.of(1, 2)))).isEqualTo("ok");
        server.verify();
    }
}
