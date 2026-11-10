package io.github.programming0026.juniverse.client.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Manejador de errores por defecto.
 *
 * Lanza {@link HttpClientErrorException} para respuestas 4xx y {@link HttpServerErrorException} para
 * 5xx (la subclase específica, por ejemplo {@code NotFound} o {@code ServiceUnavailable}), reintenta
 * 502/503/504 y cuenta los 5xx como falla para el circuit breaker.
 *
 * @author Java Universe
 */
public class DefaultErrorHandler implements ErrorHandler {

    /**
     * Lanza la excepción HTTP correspondiente con el cuerpo de la respuesta en el mensaje. Si la excepción
     * original es un {@link HttpStatusCodeException}, conserva su texto de estado, headers y cuerpo.
     *
     * @param statusCode el código de estado
     * @param responseBody el cuerpo de la respuesta
     * @param exception la excepción original
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
     * Reintenta solo 502, 503 y 504.
     *
     * @param statusCode el código de estado
     * @return true si se debe reintentar
     */
    @Override
    public boolean shouldRetry(HttpStatusCode statusCode) {
        return statusCode.value() == 502
                || statusCode.value() == 503
                || statusCode.value() == 504;
    }

    /**
     * Cuenta como falla cualquier 5xx.
     *
     * @param statusCode el código de estado
     * @return true si se registra como falla
     */
    @Override
    public boolean shouldRecordAsFailure(HttpStatusCode statusCode) {

        return statusCode.is5xxServerError();
    }
}
