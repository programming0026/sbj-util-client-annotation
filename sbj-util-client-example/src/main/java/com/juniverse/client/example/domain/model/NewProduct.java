package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.math.BigDecimal;

/**
 * Datos para crear un producto. El nombre y el precio son obligatorios.
 *
 * @param title nombre
 * @param description descripción, o null
 * @param category categoría, o null
 * @param price precio (0 o más)
 * @param stock unidades disponibles (0 o más), o null
 * @param brand marca, o null
 * @author Java Universe
 */
public record NewProduct(String title, String description, String category, BigDecimal price, Integer stock,
                         String brand) {

    /**
     * Valida los datos obligatorios.
     *
     * @throws InvalidRequestException si falta el nombre o el precio, o hay valores negativos
     */
    public NewProduct {
        if (title == null || title.isBlank()) {
            throw new InvalidRequestException("title is required");
        }
        if (price == null || price.signum() < 0) {
            throw new InvalidRequestException("price is required and must not be negative");
        }
        if (stock != null && stock < 0) {
            throw new InvalidRequestException("stock must not be negative");
        }
        title = title.trim();
    }
}
