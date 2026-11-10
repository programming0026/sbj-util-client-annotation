package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

/**
 * Pide una página de resultados.
 *
 * @param limit cuántos elementos como máximo (de 1 a {@value #MAX_LIMIT})
 * @param skip cuántos elementos se omiten antes de la página (0 o más)
 * @author Java Universe
 */
public record PageRequest(int limit, int skip) {

    /** Tamaño de página por defecto. */
    public static final int DEFAULT_LIMIT = 30;

    /** Tamaño máximo de página: evita pedir catálogos enteros por accidente. */
    public static final int MAX_LIMIT = 100;

    /**
     * Valida el rango de los valores.
     *
     * @throws InvalidRequestException si algún valor está fuera de rango
     */
    public PageRequest {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (skip < 0) {
            throw new InvalidRequestException("skip must not be negative");
        }
    }

    /**
     * Primera página con el tamaño por defecto.
     *
     * @return la primera página
     */
    public static PageRequest first() {
        return new PageRequest(DEFAULT_LIMIT, 0);
    }
}
