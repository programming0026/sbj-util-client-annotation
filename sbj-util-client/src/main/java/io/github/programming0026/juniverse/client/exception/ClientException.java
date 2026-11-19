package io.github.programming0026.juniverse.client.exception;

import java.io.Serial;

/**
 * Library exception: configuration or registration errors, missing required values or
 * exhausted timeouts. HTTP errors are not wrapped in this exception.
 *
 * @author Java Universe
 */
public class ClientException extends RuntimeException {
    /**
     * Version identifier for serialization.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public ClientException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and its cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception from its cause.
     *
     * @param cause the cause
     */
    public ClientException(Throwable cause) {
        super(cause);
    }
}
