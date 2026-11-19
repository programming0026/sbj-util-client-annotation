package com.juniverse.client.example.domain.exception;

/**
 * Base of the domain exceptions: business errors independent of any technology.
 *
 * The adapters translate technical errors (HTTP, network) into these exceptions, and the input adapters
 * translate them into their own protocol (for example, HTTP status codes).
 *
 * @author Java Universe
 */
public abstract class DomainException extends RuntimeException {

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    protected DomainException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and its cause.
     *
     * @param message the message
     * @param cause the cause
     */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
