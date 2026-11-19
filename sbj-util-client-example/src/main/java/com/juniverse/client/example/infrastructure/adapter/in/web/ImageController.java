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
@Tag(name = "Images", description = "Placeholder image generator")
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
    @Operation(summary = "Generate a placeholder image",
            description = "Returns the image bytes with the content type of the chosen format.")
    @ApiResponse(responseCode = "200", description = "The image",
            content = {
                @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
                @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "400", description = "Invalid size, color, font size or format", content = @Content)
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable", content = @Content)
    public ResponseEntity<byte[]> generate(
            @Parameter(description = "Width in pixels (1 to 2000)", example = "400")
            @RequestParam(defaultValue = "300") int width,
            @Parameter(description = "Height in pixels (1 to 2000)", example = "200")
            @RequestParam(defaultValue = "200") int height,
            @Parameter(description = "Background color: 6 hexadecimal digits without '#'", example = "008080")
            @RequestParam(required = false) String background,
            @Parameter(description = "Text color: 6 hexadecimal digits without '#'", example = "ffffff")
            @RequestParam(required = false) String color,
            @Parameter(description = "Text to draw", example = "Hello world")
            @RequestParam(required = false) String text,
            @Parameter(description = "Font size (1 to 200)", example = "24")
            @RequestParam(required = false) Integer fontSize,
            @Parameter(description = "Image format: png (default), jpg or webp", example = "webp")
            @RequestParam(required = false) String format) {
        ImageSpec spec = new ImageSpec(width, height, background, color, text, fontSize, ImageFormat.from(format));
        GeneratedImage image = generateImage.generate(spec);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.content());
    }
}
