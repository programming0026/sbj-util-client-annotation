package com.juniverse.client.example.domain.model;

/**
 * Generated image.
 *
 * @param content the image bytes
 * @param contentType the content type, for example {@code image/png}
 * @author Java Universe
 */
public record GeneratedImage(byte[] content, String contentType) {

    /** Copies the bytes so that the image is immutable. */
    public GeneratedImage {
        content = content == null ? new byte[0] : content.clone();
    }

    /**
     * The image bytes (a copy).
     *
     * @return the bytes
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    /** Does not print the bytes. */
    @Override
    public String toString() {
        return "GeneratedImage[contentType=" + contentType + ", size=" + content.length + " bytes]";
    }
}
