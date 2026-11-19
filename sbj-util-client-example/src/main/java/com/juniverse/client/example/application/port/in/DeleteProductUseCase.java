package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.Product;

/**
 * Use case: delete a product.
 *
 * @author Java Universe
 */
public interface DeleteProductUseCase {

    /**
     * Deletes a product.
     *
     * @param id the identifier
     * @return the deleted product
     * @throws ProductNotFoundException if it does not exist
     */
    Product delete(long id);
}
