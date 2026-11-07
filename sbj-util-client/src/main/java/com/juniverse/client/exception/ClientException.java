package com.juniverse.client.exception;

import java.io.Serial;

/**
 * The Class ClientException.
 */
public class ClientException extends RuntimeException {
    /**
     * The Constant serialVersionUID.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new client exception.
     *
     * @param message the message
     */
    public ClientException(String message) {
        super(message);
    }

    /**
     * Instantiates a new client exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Instantiates a new client exception.
     *
     * @param cause the cause
     */
    public ClientException(Throwable cause) {
        super(cause);
    }
}
