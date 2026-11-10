package com.juniverse.client.example.application.service;

import com.juniverse.client.example.application.port.in.GenerateImageUseCase;
import com.juniverse.client.example.application.port.out.ImageGenerator;
import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageSpec;

import java.util.Objects;

/**
 * Caso de uso de generación de imágenes: delega en el puerto {@link ImageGenerator}.
 *
 * @author Java Universe
 */
public class ImageService implements GenerateImageUseCase {

    private final ImageGenerator imageGenerator;

    /**
     * Crea el servicio.
     *
     * @param imageGenerator el puerto del generador de imágenes
     */
    public ImageService(ImageGenerator imageGenerator) {
        this.imageGenerator = Objects.requireNonNull(imageGenerator, "imageGenerator");
    }

    @Override
    public GeneratedImage generate(ImageSpec spec) {
        return imageGenerator.generate(Objects.requireNonNull(spec, "spec"));
    }
}
