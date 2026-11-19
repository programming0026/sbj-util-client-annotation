package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product in the example's REST API.
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
public record ProductResponse(long id, String title, String description, String category, BigDecimal price,
                              double discountPercentage, double rating, int stock, List<String> tags,
                              String brand, String sku, String thumbnail, List<String> images) {

    /**
     * Converts a domain product.
     *
     * @param product the product
     * @return the response
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.title(), product.description(), product.category(),
                product.price(), product.discountPercentage(), product.rating(), product.stock(), product.tags(),
                product.brand(), product.sku(), product.thumbnail(), product.images());
    }
}
