package com.juniverse.client.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

/**
 * The Class DefaultErrorHandler.
 */
public class DefaultErrorHandler implements ErrorHandler {
    
    /**
     * Handle error.
     *
     * @param statusCode the status code
     * @param responseBody the response body
     * @param exception the exception
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
     * Should retry.
     *
     * @param statusCode the status code
     * @return true, if successful
     */
    @Override
    public boolean shouldRetry(HttpStatusCode statusCode) {
        return  statusCode.value() == 502
        		|| statusCode.value() == 503 
        		|| statusCode.value() == 504;
    }

    /**
     * Should record as failure.
     *
     * @param statusCode the status code
     * @return true, if successful
     */
    @Override
    public boolean shouldRecordAsFailure(HttpStatusCode statusCode) {

        return statusCode.is5xxServerError();
    }
}
