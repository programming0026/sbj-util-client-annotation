package com.juniverse.client.example.application.service;

import com.juniverse.client.example.application.port.in.GenerateImageUseCase;
import com.juniverse.client.example.application.port.out.ImageGenerator;
import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageSpec;

import java.util.Objects;

/**
 * Image generation use case: delegates to the {@link ImageGenerator} port.
 *
 * @author Java Universe
 */
public class ImageService implements GenerateImageUseCase {

    private final ImageGenerator imageGenerator;

    /**
     * Creates the service.
     *
     * @param imageGenerator the image generator port
     */
    public ImageService(ImageGenerator imageGenerator) {
        this.imageGenerator = Objects.requireNonNull(imageGenerator, "imageGenerator");
    }

    @Override
    public GeneratedImage generate(ImageSpec spec) {
        return imageGenerator.generate(Objects.requireNonNull(spec, "spec"));
    }
}
