package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.math.BigDecimal;

/**
 * Cambios parciales a un producto: solo se modifican los campos que no son null, y al menos uno debe venir.
 *
 * @param title nuevo nombre, o null
 * @param description nueva descripción, o null
 * @param category nueva categoría, o null
 * @param price nuevo precio (0 o más), o null
 * @param stock nuevas unidades (0 o más), o null
 * @param brand nueva marca, o null
 */
public record ProductChanges(String title, String description, String category, BigDecimal price, Integer stock,
                             String brand) {

    /**
     * Valida que haya al menos un cambio y que los valores sean correctos.
     *
     * @throws InvalidRequestException si no hay cambios o hay valores inválidos
     */
    public ProductChanges {
        if (title == null && description == null && category == null && price == null && stock == null
                && brand == null) {
            throw new InvalidRequestException("at least one field to change is required");
        }
        if (title != null && title.isBlank()) {
            throw new InvalidRequestException("title must not be blank");
        }
        if (price != null && price.signum() < 0) {
            throw new InvalidRequestException("price must not be negative");
        }
        if (stock != null && stock < 0) {
            throw new InvalidRequestException("stock must not be negative");
        }
    }
}
