package com.juniverse.client.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Define cómo se tratan las respuestas HTTP con error. Las implementaciones necesitan un constructor
 * sin argumentos.
 */
public interface ErrorHandler {

    /**
     * Procesa una respuesta con error. Puede lanzar su propia excepción; si no lanza nada, se propaga
     * la excepción HTTP original.
     *
     * @param statusCode el código de estado
     * @param responseBody el cuerpo de la respuesta
     * @param exception la excepción original
     */
    void handleError(HttpStatusCode statusCode, String responseBody, Exception exception);

    /**
     * Indica si una respuesta con este estado se debe reintentar.
     *
     * @param statusCode el código de estado
     * @return true si se debe reintentar
     */
    boolean shouldRetry(HttpStatusCode statusCode);

    /**
     * Indica si este estado cuenta como falla para el circuit breaker. Si es true, la excepción original
     * se propaga sin pasar por {@link #handleError}.
     *
     * @param statusCode el código de estado
     * @return true si se registra como falla
     */
    boolean shouldRecordAsFailure(HttpStatusCode statusCode);
}
