package com.juniverse.client.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

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
                .isInstanceOf(HttpClientErrorException.class)
                .hasMessageContaining(expectedMessage)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode().value())
                        .isEqualTo(status));
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
