package io.github.programming0026.juniverse.client.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Define cómo se tratan las respuestas HTTP con error. Si existe un bean de Spring del tipo declarado se
 * usa ese; si no, Spring crea la instancia con inyección de dependencias.
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
     * Indica si este estado cuenta como falla para el circuit breaker. Se evalúa sobre la excepción que
     * finalmente se propaga (la de {@link #handleError} o la original).
     *
     * @param statusCode el código de estado
     * @return true si se registra como falla
     */
    boolean shouldRecordAsFailure(HttpStatusCode statusCode);
}
