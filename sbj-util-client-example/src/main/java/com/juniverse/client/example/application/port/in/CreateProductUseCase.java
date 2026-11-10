package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;

/**
 * Caso de uso: crear un producto.
 *
 * @author Java Universe
 */
public interface CreateProductUseCase {

    /**
     * Crea un producto.
     *
     * @param newProduct los datos del producto
     * @return el producto creado, con su identificador
     */
    Product create(NewProduct newProduct);
}
