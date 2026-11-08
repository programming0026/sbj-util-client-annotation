package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.ProductCategory;

/**
 * Categoría en la API REST del ejemplo.
 *
 * @param slug identificador
 * @param name nombre para mostrar
 */
public record CategoryResponse(String slug, String name) {

    /**
     * Convierte una categoría del dominio.
     *
     * @param category la categoría
     * @return la respuesta
     */
    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(category.slug(), category.name());
    }
}
