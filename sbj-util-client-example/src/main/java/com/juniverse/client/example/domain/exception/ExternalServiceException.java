package com.juniverse.client.example.domain.exception;

/**
 * The external service is unavailable: network failure, timeout, 5xx error or open circuit.
 *
 * @author Java Universe
 */
public class ExternalServiceException extends DomainException {

    /**
     * Creates the exception.
     *
     * @param message which operation failed
     * @param cause the technical cause
     */
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
