package com.juniverse.client.example.domain.exception;

/** El producto solicitado no existe en el catálogo. */
public class ProductNotFoundException extends DomainException {

    /**
     * Crea la excepción.
     *
     * @param id el identificador del producto que no se encontró
     */
    public ProductNotFoundException(long id) {
        super("Product with id " + id + " not found");
    }
}
