package com.juniverse.client.example.domain.model;

/**
 * Categoría de productos.
 *
 * @param slug identificador usable en filtros
 * @param name nombre para mostrar
 * @author Java Universe
 */
public record ProductCategory(String slug, String name) {
}
