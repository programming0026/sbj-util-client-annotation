package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Producto tal como lo devuelve DummyJSON. Los campos que el ejemplo no usa (reseñas, dimensiones, etc.) se
 * ignoran, y los que faltan (por ejemplo en la respuesta de crear) quedan en null.
 *
 * Los números son tipos envoltorio a propósito: DummyJSON omite campos en algunas respuestas (por ejemplo
 * al crear o modificar) y Jackson 3 rechaza un campo primitivo ausente.
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
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        Long id,
        String title,
        String description,
        String category,
        BigDecimal price,
        Double discountPercentage,
        Double rating,
        Integer stock,
        List<String> tags,
        String brand,
        String sku,
        String thumbnail,
        List<String> images) {
}
