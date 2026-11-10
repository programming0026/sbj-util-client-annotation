package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import io.github.programming0026.juniverse.client.exception.DefaultErrorHandler;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Argumentos de tipo lista y arreglo en {@link RestHttpClientInvocationHandler}.
 *
 * @author Java Universe
 */
class RestHttpClientInvocationHandlerCollectionTest {

    private static final String BASE = "http://localhost/api";

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface CollectionClient {

        @GetMapping("/items")
        String search(@RequestParam("tag") List<String> tags,
                      @RequestParam(name = "id", required = false) int[] ids,
                      @RequestParam(name = "status", defaultValue = "active") Set<String> statuses);

        @GetMapping("/items")
        String requiredTags(@RequestParam("tag") List<String> tags);

        @GetMapping(path = "/items/{ids}", headers = "X-Role=default")
        String byIds(@PathVariable("ids") List<Long> ids,
                     @RequestHeader(name = "X-Role", required = false) String[] roles);
    }

    private static CollectionClient proxy(Object httpClient) {
        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(httpClient,
                CollectionClient.class.getAnnotation(RestHttpClient.class), new DefaultErrorHandler(),
                CollectionClient.class, new CircuitBreakerUtil());
        return (CollectionClient) Proxy.newProxyInstance(CollectionClient.class.getClassLoader(),
                new Class<?>[]{CollectionClient.class}, handler);
    }

    @Test
    void collectionsAndArraysBecomeRepeatedQueryParams() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items?tag=a&tag=b%26c&id=1&id=2&status=new&status=old"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        CollectionClient client = proxy(template);

        Set<String> statuses = new LinkedHashSet<>(List.of("new", "old"));
        assertThat(client.search(List.of("a", "b&c"), new int[]{1, 2}, statuses)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void nullElementsAreSkippedAndEmptyCollectionsUseDefaultsOrAreOmitted() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items?tag=x&status=active"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        CollectionClient client = proxy(template);

        assertThat(client.search(Arrays.asList(null, "x", null), new int[0], Set.of())).isEqualTo("ok");
        server.verify();
    }

    @Test
    void emptyRequiredCollectionIsRejected() {
        CollectionClient client = proxy(new RestTemplate());

        assertThatThrownBy(() -> client.requiredTags(List.of()))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required request parameter 'tag' is null or empty in CollectionClient.requiredTags");
        assertThatThrownBy(() -> client.byIds(List.of(), null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("path variable 'ids'");
    }

    @Test
    void pathVariableCollectionsAreCommaJoinedAndHeaderArraysAreMultiValued() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/items/1%2C2%2C3"))
                .andExpect(header("X-Role", "admin", "user"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        server.expect(requestTo(BASE + "/items/9"))
                .andExpect(header("X-Role", "default"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        CollectionClient client = proxy(builder.build());

        assertThat(client.byIds(List.of(1L, 2L, 3L), new String[]{"admin", "user"})).isEqualTo("ok");
        assertThat(client.byIds(List.of(9L), new String[0])).isEqualTo("ok");
        server.verify();
    }

    @Test
    void headerFromMappingIsKeptWhenOptionalArgumentIsNull() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/items/5"))
                .andExpect(header("X-Role", "default"))
                .andExpect(headerDoesNotExist("X-Other"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(proxy(template).byIds(List.of(5L), null)).isEqualTo("ok");
        server.verify();
    }
}
