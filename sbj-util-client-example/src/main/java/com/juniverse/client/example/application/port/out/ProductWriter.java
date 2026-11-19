package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductChanges;

import java.util.Optional;

/**
 * Output port: writing to the product catalog.
 *
 * @author Java Universe
 */
public interface ProductWriter {

    /**
     * Creates a product.
     *
     * @param newProduct the product data
     * @return the created product
     * @throws ExternalServiceException if the catalog is unavailable
     */
    Product create(NewProduct newProduct);

    /**
     * Modifies a product.
     *
     * @param id the identifier
     * @param changes the fields to change
     * @return the updated product, or empty if it does not exist
     * @throws ExternalServiceException if the catalog is unavailable
     */
    Optional<Product> update(long id, ProductChanges changes);

    /**
     * Deletes a product.
     *
     * @param id the identifier
     * @return the deleted product, or empty if it does not exist
     * @throws ExternalServiceException if the catalog is unavailable
     */
    Optional<Product> delete(long id);
}
