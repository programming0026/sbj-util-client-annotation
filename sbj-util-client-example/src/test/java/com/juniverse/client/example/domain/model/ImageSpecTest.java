package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Java Universe
 */
class ImageSpecTest {

    @Test
    void appliesDefaults() {
        ImageSpec spec = ImageSpec.ofSize(300, 200);

        assertThat(spec.sizeSegment()).isEqualTo("300x200");
        assertThat(spec.backgroundColor()).isEqualTo(ImageSpec.DEFAULT_BACKGROUND);
        assertThat(spec.textColor()).isEqualTo(ImageSpec.DEFAULT_TEXT_COLOR);
        assertThat(spec.format()).isEqualTo(ImageFormat.PNG);
        assertThat(spec.text()).isNull();
        assertThat(spec.fontSize()).isNull();
    }

    @Test
    void normalizesColorsToLowerCase() {
        ImageSpec spec = new ImageSpec(10, 10, "AABBCC", " 00FF00 ", "  ", null, null);

        assertThat(spec.backgroundColor()).isEqualTo("aabbcc");
        assertThat(spec.textColor()).isEqualTo("00ff00");
        assertThat(spec.text()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#aabbcc", "abc", "gggggg", "aabbccdd"})
    void rejectsInvalidColors(String color) {
        assertThatThrownBy(() -> new ImageSpec(10, 10, color, null, null, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("backgroundColor must be a 6-digit hex color without '#'");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 2001})
    void rejectsInvalidSizes(int side) {
        assertThatThrownBy(() -> ImageSpec.ofSize(side, 100))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("width and height must be between 1 and 2000");
        assertThatThrownBy(() -> ImageSpec.ofSize(100, side)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsInvalidFontSize() {
        assertThatThrownBy(() -> new ImageSpec(10, 10, null, null, "Hi", 0, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("fontSize must be between 1 and 200");
        assertThat(new ImageSpec(10, 10, null, null, "Hi", 24, null).fontSize()).isEqualTo(24);
    }

    @Test
    void parsesImageFormats() {
        assertThat(ImageFormat.from(null)).isEqualTo(ImageFormat.PNG);
        assertThat(ImageFormat.from("WEBP")).isEqualTo(ImageFormat.WEBP);
        assertThat(ImageFormat.from("jpeg")).isEqualTo(ImageFormat.JPG);
        assertThat(ImageFormat.JPG.queryValue()).isEqualTo("jpg");
        assertThatThrownBy(() -> ImageFormat.from("gif"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("format must be png, jpg or webp but was 'gif'");
    }

    @Test
    void generatedImageIsImmutableAndDoesNotPrintItsBytes() {
        byte[] original = {1, 2, 3};
        GeneratedImage image = new GeneratedImage(original, "image/png");

        original[0] = 9;
        image.content()[1] = 9;

        assertThat(image.content()).containsExactly(1, 2, 3);
        assertThat(image.toString()).isEqualTo("GeneratedImage[contentType=image/png, size=3 bytes]");
    }
}
