package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductChanges;

import java.util.Optional;

/** Puerto de salida: escritura en el catálogo de productos. */
public interface ProductWriter {

    /**
     * Crea un producto.
     *
     * @param newProduct los datos del producto
     * @return el producto creado
     * @throws ExternalServiceException si el catálogo no está disponible
     */
    Product create(NewProduct newProduct);

    /**
     * Modifica un producto.
     *
     * @param id el identificador
     * @param changes los campos a cambiar
     * @return el producto actualizado, o vacío si no existe
     * @throws ExternalServiceException si el catálogo no está disponible
     */
    Optional<Product> update(long id, ProductChanges changes);

    /**
     * Elimina un producto.
     *
     * @param id el identificador
     * @return el producto eliminado, o vacío si no existe
     * @throws ExternalServiceException si el catálogo no está disponible
     */
    Optional<Product> delete(long id);
}
