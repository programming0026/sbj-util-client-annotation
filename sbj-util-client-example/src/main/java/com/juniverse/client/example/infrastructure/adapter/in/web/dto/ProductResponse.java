package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Producto en la API REST del ejemplo.
 *
 * @param id identificador
 * @param title nombre
 * @param description descripción
 * @param category categoría
 * @param price precio
 * @param discountPercentage porcentaje de descuento
 * @param rating valoración
 * @param stock unidades disponibles
 * @param tags etiquetas
 * @param brand marca
 * @param sku código de inventario
 * @param thumbnail miniatura
 * @param images imágenes
 */
public record ProductResponse(long id, String title, String description, String category, BigDecimal price,
                              double discountPercentage, double rating, int stock, List<String> tags,
                              String brand, String sku, String thumbnail, List<String> images) {

    /**
     * Convierte un producto del dominio.
     *
     * @param product el producto
     * @return la respuesta
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.title(), product.description(), product.category(),
                product.price(), product.discountPercentage(), product.rating(), product.stock(), product.tags(),
                product.brand(), product.sku(), product.thumbnail(), product.images());
    }
}
