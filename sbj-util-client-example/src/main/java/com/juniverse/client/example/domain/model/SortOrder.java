package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Locale;

/**
 * Sentido del ordenamiento.
 *
 * @author Java Universe
 */
public enum SortOrder {

    /** De menor a mayor. */
    ASC,

    /** De mayor a menor. */
    DESC;

    /**
     * Convierte un texto ({@code asc} o {@code desc}, sin distinguir mayúsculas).
     *
     * @param value el texto; si es nulo o vacío se usa {@link #ASC}
     * @return el sentido
     * @throws InvalidRequestException si el texto no es válido
     */
    public static SortOrder from(String value) {
        if (value == null || value.isBlank()) {
            return ASC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("order must be 'asc' or 'desc' but was '" + value + "'");
        }
    }

    /**
     * Texto en minúsculas, como lo esperan las APIs externas.
     *
     * @return {@code asc} o {@code desc}
     */
    public String lowerCase() {
        return name().toLowerCase(Locale.ROOT);
    }
}
