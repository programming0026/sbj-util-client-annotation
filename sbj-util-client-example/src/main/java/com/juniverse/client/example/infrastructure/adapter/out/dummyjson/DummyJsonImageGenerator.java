package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import com.juniverse.client.example.application.port.out.ImageGenerator;
import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageSpec;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api.ImagesApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Adaptador de salida: genera imágenes con el cliente declarativo {@link ImagesApi}. */
@Component
public class DummyJsonImageGenerator implements ImageGenerator {

    private static final String UNKNOWN_CONTENT_TYPE = "application/octet-stream";

    private final ImagesApi imagesApi;
    private final DummyJsonErrorTranslator errorTranslator;

    /**
     * Crea el adaptador.
     *
     * @param imagesApi el cliente declarativo generado por la librería
     * @param errorTranslator el traductor de errores técnicos
     */
    public DummyJsonImageGenerator(ImagesApi imagesApi, DummyJsonErrorTranslator errorTranslator) {
        this.imagesApi = imagesApi;
        this.errorTranslator = errorTranslator;
    }

    @Override
    public GeneratedImage generate(ImageSpec spec) {
        try {
            ResponseEntity<byte[]> response = imagesApi.generate(spec.sizeSegment(), spec.backgroundColor(),
                    spec.textColor(), spec.text(), spec.fontSize(), spec.format().queryValue());
            MediaType contentType = response.getHeaders().getContentType();
            return new GeneratedImage(response.getBody(),
                    contentType == null ? UNKNOWN_CONTENT_TYPE : contentType.getType() + "/" + contentType.getSubtype());
        } catch (RuntimeException e) {
            throw errorTranslator.translate("generate an image", e);
        }
    }
}
