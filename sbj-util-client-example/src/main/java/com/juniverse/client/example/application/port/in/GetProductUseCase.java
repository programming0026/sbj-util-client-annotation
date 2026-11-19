package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.Product;

/**
 * Use case: get a product by its identifier.
 *
 * @author Java Universe
 */
public interface GetProductUseCase {

    /**
     * Gets a product.
     *
     * @param id the identifier
     * @return the product
     * @throws ProductNotFoundException if it does not exist
     */
    Product getById(long id);
}
