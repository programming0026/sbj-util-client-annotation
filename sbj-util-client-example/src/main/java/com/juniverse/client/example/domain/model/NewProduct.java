package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.math.BigDecimal;

/**
 * Data to create a product. The name and the price are mandatory.
 *
 * @param title name
 * @param description description, or null
 * @param category category, or null
 * @param price price (0 or more)
 * @param stock available units (0 or more), or null
 * @param brand brand, or null
 * @author Java Universe
 */
public record NewProduct(String title, String description, String category, BigDecimal price, Integer stock,
                         String brand) {

    /**
     * Validates the mandatory data.
     *
     * @throws InvalidRequestException if the name or the price is missing, or there are negative values
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
