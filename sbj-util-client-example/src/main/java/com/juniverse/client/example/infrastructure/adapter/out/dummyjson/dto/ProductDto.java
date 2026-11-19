package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product as returned by DummyJSON. The fields the example does not use (reviews, dimensions, etc.) are
 * ignored, and those that are missing (for example in the create response) are left as null.
 *
 * The numbers are wrapper types on purpose: DummyJSON omits fields in some responses (for example
 * when creating or modifying) and Jackson 3 rejects a missing primitive field.
 *
 * @param id identifier
 * @param title name
 * @param description description
 * @param category category
 * @param price price
 * @param discountPercentage discount percentage
 * @param rating rating
 * @param stock available units
 * @param tags tags
 * @param brand brand
 * @param sku stock keeping unit code
 * @param thumbnail thumbnail
 * @param images images
 * @author Java Universe
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
