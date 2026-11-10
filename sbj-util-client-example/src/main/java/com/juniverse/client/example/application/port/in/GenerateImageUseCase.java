package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageSpec;

/**
 * Caso de uso: generar una imagen de marcador de posición.
 *
 * @author Java Universe
 */
public interface GenerateImageUseCase {

    /**
     * Genera una imagen.
     *
     * @param spec cómo debe ser la imagen
     * @return la imagen generada
     */
    GeneratedImage generate(ImageSpec spec);
}
