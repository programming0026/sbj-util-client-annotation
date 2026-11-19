package io.github.programming0026.juniverse.client.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Default error handler.
 *
 * It throws {@link HttpClientErrorException} for 4xx responses and {@link HttpServerErrorException} for
 * 5xx (the specific subclass, for example {@code NotFound} or {@code ServiceUnavailable}), retries
 * 502/503/504 and counts 5xx responses as failures for the circuit breaker.
 *
 * @author Java Universe
 */
public class DefaultErrorHandler implements ErrorHandler {

    /**
     * Throws the corresponding HTTP exception with the response body in the message. If the original
     * exception is an {@link HttpStatusCodeException}, it keeps its status text, headers and body.
     *
     * @param statusCode the status code
     * @param responseBody the response body
     * @param exception the original exception
     */
    @Override
    public void handleError(HttpStatusCode statusCode, String responseBody, Exception exception) {
        String statusText = "";
        HttpHeaders headers = null;
        byte[] body = responseBody != null ? responseBody.getBytes(StandardCharsets.UTF_8) : null;
        Charset charset = StandardCharsets.UTF_8;
        if (exception instanceof HttpStatusCodeException original) {
            statusText = original.getStatusText();
            headers = original.getResponseHeaders();
            body = original.getResponseBodyAsByteArray();
            charset = null;
        }
        if (statusCode.is4xxClientError()) {
            throw HttpClientErrorException.create("Client error: " + responseBody, statusCode, statusText,
                    headers, body, charset);
        } else if (statusCode.is5xxServerError()) {
            throw HttpServerErrorException.create("Server error: " + responseBody, statusCode, statusText,
                    headers, body, charset);
        }
    }

    /**
     * Retries only 502, 503 and 504.
     *
     * @param statusCode the status code
     * @return true if it should be retried
     */
    @Override
    public boolean shouldRetry(HttpStatusCode statusCode) {
        return statusCode.value() == 502
                || statusCode.value() == 503
                || statusCode.value() == 504;
    }

    /**
     * Counts any 5xx as a failure.
     *
     * @param statusCode the status code
     * @return true if it is recorded as a failure
     */
    @Override
    public boolean shouldRecordAsFailure(HttpStatusCode statusCode) {

        return statusCode.is5xxServerError();
    }
}
