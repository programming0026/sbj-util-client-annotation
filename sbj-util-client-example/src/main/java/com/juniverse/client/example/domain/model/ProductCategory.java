package com.juniverse.client.example.domain.model;

/**
 * Product category.
 *
 * @param slug identifier usable in filters
 * @param name display name
 * @author Java Universe
 */
public record ProductCategory(String slug, String name) {
}
