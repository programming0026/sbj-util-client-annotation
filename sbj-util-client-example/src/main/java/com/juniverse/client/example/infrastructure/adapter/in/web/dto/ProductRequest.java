package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.ProductChanges;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Body to create or modify a product in the example's REST API. The rules (name and price
 * mandatory when creating, at least one field when modifying) are validated by the domain.
 *
 * @param title name
 * @param description description
 * @param category category
 * @param price price
 * @param stock available units
 * @param brand brand
 * @author Java Universe
 */
public record ProductRequest(
        @Schema(description = "Product name (required when creating)", example = "Desk lamp") String title,
        @Schema(description = "Description", example = "Adjustable LED lamp") String description,
        @Schema(description = "Category slug", example = "furniture") String category,
        @Schema(description = "Price, 0 or more (required when creating)", example = "12.5") BigDecimal price,
        @Schema(description = "Available units, 0 or more", example = "40") Integer stock,
        @Schema(description = "Brand", example = "Lumina") String brand) {

    /**
     * Converts the body into a new product.
     *
     * @return the new product
     */
    public NewProduct toNewProduct() {
        return new NewProduct(title, description, category, price, stock, brand);
    }

    /**
     * Converts the body into partial changes.
     *
     * @return the changes
     */
    public ProductChanges toChanges() {
        return new ProductChanges(title, description, category, price, stock, brand);
    }
}
