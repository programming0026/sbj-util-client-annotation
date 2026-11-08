package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Categoría de DummyJSON.
 *
 * @param slug identificador
 * @param name nombre para mostrar
 * @param url URL de la categoría (no se usa)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryDto(String slug, String name, String url) {
}
