package com.juniverse.client.example.domain.exception;

/** El usuario o la contraseña no son correctos. */
public class InvalidCredentialsException extends DomainException {

    /** Crea la excepción. */
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
