package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductChanges;

/**
 * Use case: partially modify a product.
 *
 * @author Java Universe
 */
public interface UpdateProductUseCase {

    /**
     * Modifies a product.
     *
     * @param id the identifier
     * @param changes the fields to change
     * @return the updated product
     * @throws ProductNotFoundException if it does not exist
     */
    Product update(long id, ProductChanges changes);
}
