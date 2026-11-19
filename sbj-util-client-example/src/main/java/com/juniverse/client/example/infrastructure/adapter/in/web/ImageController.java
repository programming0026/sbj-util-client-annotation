package com.juniverse.client.example.infrastructure.adapter.in.web;

import com.juniverse.client.example.application.port.in.GenerateImageUseCase;
import com.juniverse.client.example.domain.model.GeneratedImage;
import com.juniverse.client.example.domain.model.ImageFormat;
import com.juniverse.client.example.domain.model.ImageSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Input adapter: generation of placeholder images.
 *
 * @author Java Universe
 */
@RestController
@RequestMapping("/api/images")
@Tag(name = "Imágenes", description = "Generador de imágenes de marcador de posición")
public class ImageController {

    private final GenerateImageUseCase generateImage;

    /**
     * Creates the controller.
     *
     * @param generateImage image generation use case
     */
    public ImageController(GenerateImageUseCase generateImage) {
        this.generateImage = generateImage;
    }

    /**
     * Generates an image and returns it with its content type.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param background background color in hexadecimal, without {@code #}
     * @param color text color in hexadecimal, without {@code #}
     * @param text text to draw
     * @param fontSize font size
     * @param format {@code png}, {@code jpg} or {@code webp}
     * @return the image bytes
     */
    @GetMapping
    @Operation(summary = "Generar una imagen de marcador de posición",
            description = "Devuelve los bytes de la imagen con el tipo de contenido del formato elegido.")
    @ApiResponse(responseCode = "200", description = "La imagen",
            content = {
                @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
                @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "400", description = "Tamaño, color, tamaño de letra o formato inválidos", content = @Content)
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible", content = @Content)
    public ResponseEntity<byte[]> generate(
            @Parameter(description = "Ancho en píxeles (de 1 a 2000)", example = "400")
            @RequestParam(defaultValue = "300") int width,
            @Parameter(description = "Alto en píxeles (de 1 a 2000)", example = "200")
            @RequestParam(defaultValue = "200") int height,
            @Parameter(description = "Color de fondo: 6 dígitos hexadecimales sin '#'", example = "008080")
            @RequestParam(required = false) String background,
            @Parameter(description = "Color del texto: 6 dígitos hexadecimales sin '#'", example = "ffffff")
            @RequestParam(required = false) String color,
            @Parameter(description = "Texto a dibujar", example = "Hola mundo")
            @RequestParam(required = false) String text,
            @Parameter(description = "Tamaño de la letra (de 1 a 200)", example = "24")
            @RequestParam(required = false) Integer fontSize,
            @Parameter(description = "Formato de la imagen: png (por defecto), jpg o webp", example = "webp")
            @RequestParam(required = false) String format) {
        ImageSpec spec = new ImageSpec(width, height, background, color, text, fontSize, ImageFormat.from(format));
        GeneratedImage image = generateImage.generate(spec);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.content());
    }
}
