package com.juniverse.client.example.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catalog product.
 *
 * @param id identifier
 * @param title name
 * @param description description
 * @param category category (slug)
 * @param price price
 * @param discountPercentage discount percentage
 * @param rating average rating
 * @param stock available units
 * @param tags tags
 * @param brand brand, or null
 * @param sku stock keeping unit code, or null
 * @param thumbnail thumbnail URL, or null
 * @param images image URLs
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

    /** Copies the lists so that the product is immutable. */
    public Product {
        tags = tags == null ? List.of() : List.copyOf(tags);
        images = images == null ? List.of() : List.copyOf(images);
    }
}
