package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.Product;

/**
 * Caso de uso: consultar un producto por su identificador.
 *
 * @author Java Universe
 */
public interface GetProductUseCase {

    /**
     * Obtiene un producto.
     *
     * @param id el identificador
     * @return el producto
     * @throws ProductNotFoundException si no existe
     */
    Product getById(long id);
}
