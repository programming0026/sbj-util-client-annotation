package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Locale;

/**
 * Image formats that can be generated.
 *
 * @author Java Universe
 */
public enum ImageFormat {

    /** PNG (the default). */
    PNG("png"),

    /** JPEG. */
    JPG("jpg"),

    /** WebP. */
    WEBP("webp");

    /** Value sent to the image service. */
    private final String queryValue;

    ImageFormat(String queryValue) {
        this.queryValue = queryValue;
    }

    /**
     * Value sent to the image service.
     *
     * @return {@code png}, {@code jpg} or {@code webp}
     */
    public String queryValue() {
        return queryValue;
    }

    /**
     * Converts a text (case-insensitive; {@code jpeg} is accepted as {@code jpg}).
     *
     * @param value the text; if null or empty {@link #PNG} is used
     * @return the format
     * @throws InvalidRequestException if the format is not valid
     */
    public static ImageFormat from(String value) {
        if (value == null || value.isBlank()) {
            return PNG;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("jpeg".equals(normalized)) {
            return JPG;
        }
        for (ImageFormat format : values()) {
            if (format.queryValue.equals(normalized)) {
                return format;
            }
        }
        throw new InvalidRequestException("format must be png, jpg or webp but was '" + value + "'");
    }
}
