package com.juniverse.client.example.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Producto del catálogo.
 *
 * @param id identificador
 * @param title nombre
 * @param description descripción
 * @param category categoría (slug)
 * @param price precio
 * @param discountPercentage porcentaje de descuento
 * @param rating valoración media
 * @param stock unidades disponibles
 * @param tags etiquetas
 * @param brand marca, o null
 * @param sku código de inventario, o null
 * @param thumbnail URL de la miniatura, o null
 * @param images URLs de las imágenes
 * @author Java Universe
 */
public record Product(
        long id,
        String title,
        String description,
        String category,
        BigDecimal price,
        double discountPercentage,
        double rating,
        int stock,
        List<String> tags,
        String brand,
        String sku,
        String thumbnail,
        List<String> images) {

    /** Copia las listas para que el producto sea inmutable. */
    public Product {
        tags = tags == null ? List.of() : List.copyOf(tags);
        images = images == null ? List.of() : List.copyOf(images);
    }
}
