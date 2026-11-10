package com.juniverse.client.example.domain.exception;

/**
 * Los datos de entrada no cumplen las reglas del dominio (valores fuera de rango, campos vacíos, etc.).
 *
 * @author Java Universe
 */
public class InvalidRequestException extends DomainException {

    /**
     * Crea la excepción.
     *
     * @param message qué regla se incumplió
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
