package io.github.programming0026.juniverse.client.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Defines how HTTP error responses are handled. If a Spring bean of the declared type exists it is
 * used; otherwise Spring creates the instance with dependency injection.
 *
 * @author Java Universe
 */
public interface ErrorHandler {

    /**
     * Processes an error response. It may throw its own exception; if it throws nothing, the original
     * HTTP exception is propagated.
     *
     * @param statusCode the status code
     * @param responseBody the response body
     * @param exception the original exception
     */
    void handleError(HttpStatusCode statusCode, String responseBody, Exception exception);

    /**
     * Indicates whether a response with this status should be retried.
     *
     * @param statusCode the status code
     * @return true if it should be retried
     */
    boolean shouldRetry(HttpStatusCode statusCode);

    /**
     * Indicates whether this status counts as a failure for the circuit breaker. It is evaluated on the
     * exception that is finally propagated (the one from {@link #handleError} or the original one).
     *
     * @param statusCode the status code
     * @return true if it is recorded as a failure
     */
    boolean shouldRecordAsFailure(HttpStatusCode statusCode);
}
