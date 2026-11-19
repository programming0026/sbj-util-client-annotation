package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.ProductCategory;

/**
 * Category in the example's REST API.
 *
 * @param slug identifier
 * @param name display name
 * @author Java Universe
 */
public record CategoryResponse(String slug, String name) {

    /**
     * Converts a domain category.
     *
     * @param category the category
     * @return the response
     */
    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(category.slug(), category.name());
    }
}
