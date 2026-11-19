package com.juniverse.client.example.domain.exception;

/**
 * The user name or the password is not correct.
 *
 * @author Java Universe
 */
public class InvalidCredentialsException extends DomainException {

    /** Creates the exception. */
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
