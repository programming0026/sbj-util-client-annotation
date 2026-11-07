package com.juniverse.client.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Manejador de errores por defecto.
 *
 * Lanza {@link HttpClientErrorException} para respuestas 4xx y 5xx, reintenta 502/503/504 y cuenta
 * los 5xx como falla para el circuit breaker.
 */
public class DefaultErrorHandler implements ErrorHandler {

    /**
     * Lanza {@link HttpClientErrorException} con el cuerpo de la respuesta si el estado es 4xx o 5xx.
     *
     * @param statusCode el código de estado
     * @param responseBody el cuerpo de la respuesta
     * @param exception la excepción original
     */
    @Override
    public void handleError(HttpStatusCode statusCode, String responseBody, Exception exception) {
        if (statusCode.is4xxClientError()) {
            throw new HttpClientErrorException(statusCode, "Client error: " + responseBody);
        } else if (statusCode.is5xxServerError()) {
            throw new HttpClientErrorException(statusCode, "Server error: " + responseBody);
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
        return  statusCode.value() == 502
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
