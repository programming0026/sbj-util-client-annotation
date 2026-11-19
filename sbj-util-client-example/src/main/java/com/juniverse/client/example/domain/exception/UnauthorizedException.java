package com.juniverse.client.example.domain.exception;

/**
 * The access or refresh token is missing, invalid or expired.
 *
 * @author Java Universe
 */
public class UnauthorizedException extends DomainException {

    /**
     * Creates the exception.
     *
     * @param message why it was not authorized
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
