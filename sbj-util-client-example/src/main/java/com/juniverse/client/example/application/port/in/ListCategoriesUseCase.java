package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.ProductCategory;

import java.util.List;

/**
 * Caso de uso: listar las categorías de productos.
 *
 * @author Java Universe
 */
public interface ListCategoriesUseCase {

    /**
     * Lista las categorías.
     *
     * @return las categorías
     */
    List<ProductCategory> listCategories();
}
