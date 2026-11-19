package com.juniverse.client.example.domain.exception;

/**
 * The input data does not meet the domain rules (out-of-range values, empty fields, etc.).
 *
 * @author Java Universe
 */
public class InvalidRequestException extends DomainException {

    /**
     * Creates the exception.
     *
     * @param message which rule was violated
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
