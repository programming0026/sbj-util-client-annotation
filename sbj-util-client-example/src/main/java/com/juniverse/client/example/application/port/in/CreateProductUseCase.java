package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;

/**
 * Use case: create a product.
 *
 * @author Java Universe
 */
public interface CreateProductUseCase {

    /**
     * Creates a product.
     *
     * @param newProduct the product data
     * @return the created product, with its identifier
     */
    Product create(NewProduct newProduct);
}
