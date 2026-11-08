package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.exception.DefaultErrorHandler;
import com.juniverse.client.util.CircuitBreakerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Parámetros {@code @CookieValue}: se envían como pares del header {@code Cookie}. */
class RestHttpClientCookieTest {

    private static final String BASE = "http://localhost/api";

    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface CookieClient {

        @GetMapping("/me")
        String single(@CookieValue("session") String session);

        @GetMapping("/me")
        String several(@CookieValue("session") String session, @CookieValue("theme") String theme);

        @GetMapping("/me")
        String optional(@CookieValue(name = "theme", required = false) String theme);

        @GetMapping("/me")
        String withDefault(@CookieValue(name = "theme", defaultValue = "light") String theme);

        @GetMapping("/me")
        String repeated(@CookieValue("tag") List<String> tags);

        @GetMapping("/me")
        String all(@CookieValue Map<String, String> cookies);

        @GetMapping(path = "/me", headers = "Cookie=fixed=1")
        String withFixed(@CookieValue("session") String session);
    }

    private static CookieClient proxy(Object httpClient) {
        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(httpClient,
                CookieClient.class.getAnnotation(RestHttpClient.class), new DefaultErrorHandler(),
                CookieClient.class, new CircuitBreakerUtil());
        return (CookieClient) Proxy.newProxyInstance(CookieClient.class.getClassLoader(),
                new Class<?>[]{CookieClient.class}, handler);
    }

    private static void respondOk(MockRestServiceServer server, String cookieHeader) {
        if (cookieHeader == null) {
            server.expect(requestTo(BASE + "/me")).andExpect(headerDoesNotExist("Cookie"))
                    .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        } else {
            server.expect(requestTo(BASE + "/me")).andExpect(header("Cookie", cookieHeader))
                    .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        }
    }

    @Test
    void aCookieValueBecomesACookiePair() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        respondOk(server, "session=abc");

        assertThat(proxy(template).single("abc")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void severalCookiesAreJoinedInDeclarationOrderInOneHeader() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        respondOk(server, "session=abc; theme=dark");

        assertThat(proxy(template).several("abc", "dark")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void worksWithRestClientToo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        respondOk(server, "session=abc; theme=dark");

        assertThat(proxy(builder.build()).several("abc", "dark")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void anOptionalCookieIsOmittedWhenNull() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        respondOk(server, null);

        assertThat(proxy(template).optional(null)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void theDefaultValueIsUsedWhenTheArgumentIsNull() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        respondOk(server, "theme=light");

        assertThat(proxy(template).withDefault(null)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void aMissingRequiredCookieFails() {
        CookieClient client = proxy(new RestTemplate());

        assertThatThrownBy(() -> client.single(null)).isInstanceOf(ClientException.class)
                .hasMessageContaining("cookie");
    }

    @Test
    void aListSendsOnePairPerValue() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        respondOk(server, "tag=a; tag=b");

        assertThat(proxy(template).repeated(List.of("a", "b"))).isEqualTo("ok");
        server.verify();
    }

    @Test
    void aMapSendsOnePairPerEntry() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        respondOk(server, "a=1; b=2");

        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("a", "1");
        cookies.put("b", "2");

        assertThat(proxy(template).all(cookies)).isEqualTo("ok");
        server.verify();
    }

    @Test
    void cookiesAreAppendedToThoseDeclaredInTheMapping() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        respondOk(server, "fixed=1; session=abc");

        assertThat(proxy(template).withFixed("abc")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void valuesThatWouldBreakTheHeaderAreRejected() {
        CookieClient client = proxy(new RestTemplate());

        assertThatThrownBy(() -> client.single("a; admin=true")).isInstanceOf(ClientException.class)
                .hasMessage("The value of cookie 'session' cannot contain ';' or line breaks");
        assertThatThrownBy(() -> client.single("a\r\nX-Evil: 1")).isInstanceOf(ClientException.class);
    }
}
