package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Describes the image to generate.
 *
 * The external service answers with an HTML error page for invalid sizes or colors, so the
 * domain validates them before calling it.
 *
 * @param width width in pixels (1 to {@value #MAX_SIDE})
 * @param height height in pixels (1 to {@value #MAX_SIDE})
 * @param backgroundColor 6-digit hexadecimal background color, without {@code #}
 * @param textColor 6-digit hexadecimal text color, without {@code #}
 * @param text text to draw, or null
 * @param fontSize font size (1 to {@value #MAX_FONT_SIZE}), or null
 * @param format output format
 * @author Java Universe
 */
public record ImageSpec(int width, int height, String backgroundColor, String textColor, String text,
                        Integer fontSize, ImageFormat format) {

    /** Maximum allowed side. */
    public static final int MAX_SIDE = 2000;

    /** Maximum allowed font size. */
    public static final int MAX_FONT_SIZE = 200;

    /** Default background color. */
    public static final String DEFAULT_BACKGROUND = "cccccc";

    /** Default text color. */
    public static final String DEFAULT_TEXT_COLOR = "969696";

    private static final Pattern HEX_COLOR = Pattern.compile("[0-9a-fA-F]{6}");

    /**
     * Applies the default values and validates the rest.
     *
     * @throws InvalidRequestException if the size, the colors or the font are out of range
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
     * Image of a given size with everything else as default.
     *
     * @param width the width
     * @param height the height
     * @return the specification
     */
    public static ImageSpec ofSize(int width, int height) {
        return new ImageSpec(width, height, null, null, null, null, null);
    }

    /**
     * Size in the {@code WIDTHxHEIGHT} format used by the external service.
     *
     * @return for example {@code 300x200}
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
