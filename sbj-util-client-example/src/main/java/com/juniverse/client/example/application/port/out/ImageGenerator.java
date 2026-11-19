package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageSpec;

/**
 * Output port: the service that generates placeholder images.
 *
 * @author Java Universe
 */
public interface ImageGenerator {

    /**
     * Generates an image.
     *
     * @param spec what the image must be like
     * @return the generated image
     * @throws ExternalServiceException if the service is unavailable
     */
    GeneratedImage generate(ImageSpec spec);
}
