package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageSpec;

/** Puerto de salida: el servicio que genera imágenes de marcador de posición. */
public interface ImageGenerator {

    /**
     * Genera una imagen.
     *
     * @param spec cómo debe ser la imagen
     * @return la imagen generada
     * @throws ExternalServiceException si el servicio no está disponible
     */
    GeneratedImage generate(ImageSpec spec);
}
