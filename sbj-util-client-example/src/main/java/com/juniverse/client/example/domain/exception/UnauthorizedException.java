package com.juniverse.client.example.domain.exception;

/** El token de acceso o de renovación falta, es inválido o expiró. */
public class UnauthorizedException extends DomainException {

    /**
     * Crea la excepción.
     *
     * @param message por qué no se autorizó
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
