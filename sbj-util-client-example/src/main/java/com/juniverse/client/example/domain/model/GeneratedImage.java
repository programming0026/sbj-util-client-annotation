package com.juniverse.client.example.domain.model;

/**
 * Imagen generada.
 *
 * @param content los bytes de la imagen
 * @param contentType el tipo de contenido, por ejemplo {@code image/png}
 */
public record GeneratedImage(byte[] content, String contentType) {

    /** Copia los bytes para que la imagen sea inmutable. */
    public GeneratedImage {
        content = content == null ? new byte[0] : content.clone();
    }

    /**
     * Los bytes de la imagen (una copia).
     *
     * @return los bytes
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    /** No imprime los bytes. */
    @Override
    public String toString() {
        return "GeneratedImage[contentType=" + contentType + ", size=" + content.length + " bytes]";
    }
}
