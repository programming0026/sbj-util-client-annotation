package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.Product;

/** Caso de uso: eliminar un producto. */
public interface DeleteProductUseCase {

    /**
     * Elimina un producto.
     *
     * @param id el identificador
     * @return el producto eliminado
     * @throws ProductNotFoundException si no existe
     */
    Product delete(long id);
}
