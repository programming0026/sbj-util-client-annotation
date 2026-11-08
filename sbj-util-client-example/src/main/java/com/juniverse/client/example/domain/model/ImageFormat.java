package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Locale;

/** Formatos de imagen que se pueden generar. */
public enum ImageFormat {

    /** PNG (por defecto). */
    PNG("png"),

    /** JPEG. */
    JPG("jpg"),

    /** WebP. */
    WEBP("webp");

    /** Valor que se envía al servicio de imágenes. */
    private final String queryValue;

    ImageFormat(String queryValue) {
        this.queryValue = queryValue;
    }

    /**
     * Valor que se envía al servicio de imágenes.
     *
     * @return {@code png}, {@code jpg} o {@code webp}
     */
    public String queryValue() {
        return queryValue;
    }

    /**
     * Convierte un texto (sin distinguir mayúsculas; {@code jpeg} se acepta como {@code jpg}).
     *
     * @param value el texto; si es nulo o vacío se usa {@link #PNG}
     * @return el formato
     * @throws InvalidRequestException si el formato no es válido
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
