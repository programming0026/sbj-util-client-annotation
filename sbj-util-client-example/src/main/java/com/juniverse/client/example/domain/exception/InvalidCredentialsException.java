package com.juniverse.client.example.domain.exception;

/**
 * El usuario o la contraseña no son correctos.
 *
 * @author Java Universe
 */
public class InvalidCredentialsException extends DomainException {

    /** Crea la excepción. */
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
