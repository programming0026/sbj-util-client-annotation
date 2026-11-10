package io.github.programming0026.juniverse.client.util;

import io.github.programming0026.juniverse.client.autoconfigure.CircuitBreakerConfig;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.SSLConfig;
import io.github.programming0026.juniverse.client.enums.ClientType;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Java Universe
 */
class AnnotationPlaceholderResolverTest {

    @RestHttpClient(name = "[name]", url = "[url]/v1", type = ClientType.REST_TEMPLATE, connectTimeout = 123,
            ssl = @SSLConfig(keystorePath = "[ks]", keystorePassword = "[pwd]"),
            circuitBreaker = @CircuitBreakerConfig(enabled = true, failureRateThreshold = 42))
    interface Annotated {

        @GetMapping(path = {"[a]", "[b]"}, headers = "X=[h]")
        void get();
    }

    /** Reemplaza "[x]" por "X" para ver qué valores pasaron por el resolvedor. */
    private static String upper(String value) {
        return value.replace("[", "").replace("]", "").toUpperCase();
    }

    @Test
    void resolvesStringAttributesIncludingNestedAnnotations() {
        RestHttpClient original = Annotated.class.getAnnotation(RestHttpClient.class);

        RestHttpClient resolved = AnnotationPlaceholderResolver.resolve(original, AnnotationPlaceholderResolverTest::upper);

        assertThat(resolved.name()).isEqualTo("NAME");
        assertThat(resolved.url()).isEqualTo("URL/V1");
        assertThat(resolved.ssl().keystorePath()).isEqualTo("KS");
        assertThat(resolved.ssl().keystorePassword()).isEqualTo("PWD");
        assertThat(resolved.ssl().enabled()).isTrue();
        assertThat(resolved.type()).isEqualTo(ClientType.REST_TEMPLATE);
        assertThat(resolved.connectTimeout()).isEqualTo(123);
        assertThat(resolved.circuitBreaker().enabled()).isTrue();
        assertThat(resolved.circuitBreaker().failureRateThreshold()).isEqualTo(42);
        assertThat(resolved.annotationType()).isEqualTo(RestHttpClient.class);
        assertThat(original.url()).isEqualTo("[url]/v1");
    }

    @Test
    void resolvesStringArrays() throws Exception {
        GetMapping original = Annotated.class.getMethod("get").getAnnotation(GetMapping.class);

        GetMapping resolved = AnnotationPlaceholderResolver.resolve(original, AnnotationPlaceholderResolverTest::upper);

        assertThat(resolved.path()).containsExactly("A", "B");
        assertThat(resolved.headers()).containsExactly("X=H");
    }
}
