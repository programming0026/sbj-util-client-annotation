package com.juniverse.client.example.domain.exception;

/**
 * The requested product does not exist in the catalog.
 *
 * @author Java Universe
 */
public class ProductNotFoundException extends DomainException {

    /**
     * Creates the exception.
     *
     * @param id the identifier of the product that was not found
     */
    public ProductNotFoundException(long id) {
        super("Product with id " + id + " not found");
    }
}
