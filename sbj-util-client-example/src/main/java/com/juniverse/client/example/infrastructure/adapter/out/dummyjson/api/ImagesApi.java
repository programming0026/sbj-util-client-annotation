package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.LoggingInterceptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cliente declarativo de https://dummyjson.com/docs/image.
 *
 * Devuelve {@code ResponseEntity<byte[]>} para conservar los bytes y el tipo de contenido
 * ({@code image/png}, {@code image/jpeg} o {@code image/webp}).
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
     * Genera una imagen.
     *
     * @param size tamaño, por ejemplo {@code 300x200}
     * @param background color de fondo en hexadecimal
     * @param color color del texto en hexadecimal
     * @param text texto a dibujar, o null
     * @param fontSize tamaño de la letra, o null
     * @param type {@code png}, {@code jpg} o {@code webp}
     * @return la imagen con su tipo de contenido
     */
    @GetMapping("/{size}/{background}/{color}")
    ResponseEntity<byte[]> generate(@PathVariable("size") String size,
                                    @PathVariable("background") String background,
                                    @PathVariable("color") String color,
                                    @RequestParam(name = "text", required = false) String text,
                                    @RequestParam(name = "fontSize", required = false) Integer fontSize,
                                    @RequestParam("type") String type);
}
