package io.github.programming0026.juniverse.client.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultErrorHandlerTest {

    private final DefaultErrorHandler handler = new DefaultErrorHandler();

    @ParameterizedTest
    @CsvSource({"400, Client error: body", "404, Client error: body", "500, Server error: body",
            "503, Server error: body"})
    void handleErrorThrowsForErrorStatuses(int status, String expectedMessage) {
        assertThatThrownBy(() -> handler.handleError(HttpStatusCode.valueOf(status), "body", null))
                .isInstanceOf(HttpStatusCodeException.class)
                .hasMessageContaining(expectedMessage)
                .satisfies(ex -> {
                    HttpStatusCodeException httpException = (HttpStatusCodeException) ex;
                    assertThat(httpException.getStatusCode().value()).isEqualTo(status);
                    assertThat(httpException.getResponseBodyAsString()).isEqualTo("body");
                });
    }

    @Test
    void clientErrorsUseClientExceptionSubclasses() {
        assertThatThrownBy(() -> handler.handleError(HttpStatus.NOT_FOUND, "x", null))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void serverErrorsUseServerExceptionSubclasses() {
        assertThatThrownBy(() -> handler.handleError(HttpStatus.SERVICE_UNAVAILABLE, "x", null))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }

    @Test
    void originalHeadersAndBodyArePreserved() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Request-Id", "42");
        HttpServerErrorException original = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway",
                headers, "{\"error\":\"upstream\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> handler.handleError(original.getStatusCode(),
                original.getResponseBodyAsString(), original))
                .isInstanceOf(HttpServerErrorException.BadGateway.class)
                .hasMessage("Server error: {\"error\":\"upstream\"}")
                .satisfies(ex -> {
                    HttpServerErrorException httpException = (HttpServerErrorException) ex;
                    assertThat(httpException.getStatusText()).isEqualTo("Bad Gateway");
                    assertThat(httpException.getResponseHeaders().getFirst("X-Request-Id")).isEqualTo("42");
                    assertThat(httpException.getResponseBodyAsString()).isEqualTo("{\"error\":\"upstream\"}");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 302})
    void handleErrorDoesNothingForNonErrorStatuses(int status) {
        assertThatCode(() -> handler.handleError(HttpStatusCode.valueOf(status), "body", null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"502, true", "503, true", "504, true", "500, false", "404, false", "200, false"})
    void shouldRetry(int status, boolean expected) {
        assertThat(handler.shouldRetry(HttpStatusCode.valueOf(status))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"500, true", "503, true", "404, false", "200, false"})
    void shouldRecordAsFailure(int status, boolean expected) {
        assertThat(handler.shouldRecordAsFailure(HttpStatusCode.valueOf(status))).isEqualTo(expected);
    }
}
