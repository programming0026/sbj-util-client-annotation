package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Describe la imagen que se quiere generar.
 *
 * El servicio externo responde con una página HTML de error ante tamaños o colores inválidos, así que el
 * dominio los valida antes de llamarlo.
 *
 * @param width ancho en píxeles (1 a {@value #MAX_SIDE})
 * @param height alto en píxeles (1 a {@value #MAX_SIDE})
 * @param backgroundColor color de fondo en hexadecimal de 6 dígitos, sin {@code #}
 * @param textColor color del texto en hexadecimal de 6 dígitos, sin {@code #}
 * @param text texto a dibujar, o null
 * @param fontSize tamaño de la letra (1 a {@value #MAX_FONT_SIZE}), o null
 * @param format formato de salida
 * @author Java Universe
 */
public record ImageSpec(int width, int height, String backgroundColor, String textColor, String text,
                        Integer fontSize, ImageFormat format) {

    /** Lado máximo permitido. */
    public static final int MAX_SIDE = 2000;

    /** Tamaño de letra máximo permitido. */
    public static final int MAX_FONT_SIZE = 200;

    /** Color de fondo por defecto. */
    public static final String DEFAULT_BACKGROUND = "cccccc";

    /** Color de texto por defecto. */
    public static final String DEFAULT_TEXT_COLOR = "969696";

    private static final Pattern HEX_COLOR = Pattern.compile("[0-9a-fA-F]{6}");

    /**
     * Aplica los valores por defecto y valida el resto.
     *
     * @throws InvalidRequestException si el tamaño, los colores o la letra están fuera de rango
     */
    public ImageSpec {
        if (width < 1 || width > MAX_SIDE || height < 1 || height > MAX_SIDE) {
            throw new InvalidRequestException("width and height must be between 1 and " + MAX_SIDE);
        }
        backgroundColor = normalizeColor(backgroundColor, DEFAULT_BACKGROUND, "backgroundColor");
        textColor = normalizeColor(textColor, DEFAULT_TEXT_COLOR, "textColor");
        text = text == null || text.isBlank() ? null : text;
        if (fontSize != null && (fontSize < 1 || fontSize > MAX_FONT_SIZE)) {
            throw new InvalidRequestException("fontSize must be between 1 and " + MAX_FONT_SIZE);
        }
        format = format == null ? ImageFormat.PNG : format;
    }

    /**
     * Imagen de un tamaño con todo lo demás por defecto.
     *
     * @param width el ancho
     * @param height el alto
     * @return la especificación
     */
    public static ImageSpec ofSize(int width, int height) {
        return new ImageSpec(width, height, null, null, null, null, null);
    }

    /**
     * Tamaño en el formato {@code ANCHOxALTO} que usa el servicio externo.
     *
     * @return por ejemplo {@code 300x200}
     */
    public String sizeSegment() {
        return width + "x" + height;
    }

    private static String normalizeColor(String value, String defaultValue, String field) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String color = value.trim();
        if (!HEX_COLOR.matcher(color).matches()) {
            throw new InvalidRequestException(field + " must be a 6-digit hex color without '#' but was '" + value + "'");
        }
        return color.toLowerCase(Locale.ROOT);
    }
}
