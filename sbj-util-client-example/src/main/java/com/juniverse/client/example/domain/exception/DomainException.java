package com.juniverse.client.example.domain.exception;

/**
 * Base de las excepciones del dominio: errores de negocio independientes de cualquier tecnología.
 *
 * Los adaptadores traducen los errores técnicos (HTTP, red) a estas excepciones, y los adaptadores de
 * entrada las traducen a su propio protocolo (por ejemplo, a códigos de estado HTTP).
 */
public abstract class DomainException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje.
     *
     * @param message el mensaje
     */
    protected DomainException(String message) {
        super(message);
    }

    /**
     * Crea la excepción con un mensaje y su causa.
     *
     * @param message el mensaje
     * @param cause la causa
     */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
