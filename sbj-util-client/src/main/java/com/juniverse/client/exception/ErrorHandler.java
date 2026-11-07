package com.juniverse.client.exception;

import org.springframework.http.HttpStatusCode;

/**
 * The Interface ErrorHandler.
 */
public interface ErrorHandler {
    
    /**
     * Handle error.
     *
     * @param statusCode the status code
     * @param responseBody the response body
     * @param exception the exception
     */
    void handleError(HttpStatusCode statusCode, String responseBody, Exception exception);

    /**
     * Should retry.
     *
     * @param statusCode the status code
     * @return true, if successful
     */
    boolean shouldRetry(HttpStatusCode statusCode);

    /**
     * Should record as failure.
     *
     * @param statusCode the status code
     * @return true, if successful
     */
    boolean shouldRecordAsFailure(HttpStatusCode statusCode);
}
