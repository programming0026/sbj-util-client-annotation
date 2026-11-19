package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.math.BigDecimal;

/**
 * Partial changes to a product: only the non-null fields are modified, and at least one must be present.
 *
 * @param title new name, or null
 * @param description new description, or null
 * @param category new category, or null
 * @param price new price (0 or more), or null
 * @param stock new units (0 or more), or null
 * @param brand new brand, or null
 * @author Java Universe
 */
public record ProductChanges(String title, String description, String category, BigDecimal price, Integer stock,
                             String brand) {

    /**
     * Validates that there is at least one change and that the values are correct.
     *
     * @throws InvalidRequestException if there are no changes or there are invalid values
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
