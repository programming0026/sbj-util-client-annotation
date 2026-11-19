package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageSpec;

/**
 * Use case: generate a placeholder image.
 *
 * @author Java Universe
 */
public interface GenerateImageUseCase {

    /**
     * Generates an image.
     *
     * @param spec what the image must be like
     * @return the generated image
     */
    GeneratedImage generate(ImageSpec spec);
}
