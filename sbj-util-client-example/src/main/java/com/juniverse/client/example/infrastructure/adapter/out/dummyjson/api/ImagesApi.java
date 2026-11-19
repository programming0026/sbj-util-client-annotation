package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.LoggingInterceptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Declarative client of https://dummyjson.com/docs/image.
 *
 * Returns {@code ResponseEntity<byte[]>} to keep the bytes and the content type
 * ({@code image/png}, {@code image/jpeg} or {@code image/webp}).
 *
 * @author Java Universe
 */
@RestHttpClient(
        name = "dummyJsonImages",
        url = "${dummyjson.base-url}/image",
        connectTimeout = 3000,
        readTimeout = 10000,
        retry = @RetryConfig(maxRetries = 2, retryDelay = 300),
        interceptors = LoggingInterceptor.class)
public interface ImagesApi {

    /**
     * Generates an image.
     *
     * @param size size, for example {@code 300x200}
     * @param background background color in hexadecimal
     * @param color text color in hexadecimal
     * @param text text to draw, or null
     * @param fontSize font size, or null
     * @param type {@code png}, {@code jpg} or {@code webp}
     * @return the image with its content type
     */
    @GetMapping("/{size}/{background}/{color}")
    ResponseEntity<byte[]> generate(@PathVariable("size") String size,
                                    @PathVariable("background") String background,
                                    @PathVariable("color") String color,
                                    @RequestParam(name = "text", required = false) String text,
                                    @RequestParam(name = "fontSize", required = false) Integer fontSize,
                                    @RequestParam("type") String type);
}
