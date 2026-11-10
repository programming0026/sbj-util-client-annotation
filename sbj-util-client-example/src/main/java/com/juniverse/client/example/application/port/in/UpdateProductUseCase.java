package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductChanges;

/**
 * Caso de uso: modificar parcialmente un producto.
 *
 * @author Java Universe
 */
public interface UpdateProductUseCase {

    /**
     * Modifica un producto.
     *
     * @param id el identificador
     * @param changes los campos a cambiar
     * @return el producto actualizado
     * @throws ProductNotFoundException si no existe
     */
    Product update(long id, ProductChanges changes);
}
