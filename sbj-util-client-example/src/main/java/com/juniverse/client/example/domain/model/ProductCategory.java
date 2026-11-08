package com.juniverse.client.example.domain.model;

/**
 * Categoría de productos.
 *
 * @param slug identificador usable en filtros
 * @param name nombre para mostrar
 */
public record ProductCategory(String slug, String name) {
}
