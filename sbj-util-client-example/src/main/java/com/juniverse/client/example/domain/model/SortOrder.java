package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Locale;

/**
 * Sort direction.
 *
 * @author Java Universe
 */
public enum SortOrder {

    /** From lowest to highest. */
    ASC,

    /** From highest to lowest. */
    DESC;

    /**
     * Converts a text ({@code asc} or {@code desc}, case-insensitive).
     *
     * @param value the text; if null or empty {@link #ASC} is used
     * @return the direction
     * @throws InvalidRequestException if the text is not valid
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
     * Lowercase text, as the external APIs expect it.
     *
     * @return {@code asc} or {@code desc}
     */
    public String lowerCase() {
        return name().toLowerCase(Locale.ROOT);
    }
}
