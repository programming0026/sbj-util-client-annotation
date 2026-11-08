package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.ProductPage;

/** Caso de uso: consultar productos (todos, por texto o por categoría), paginados y ordenados. */
public interface ListProductsUseCase {

    /**
     * Consulta una página de productos.
     *
     * @param criteria qué productos y cómo
     * @return la página de productos
     */
    ProductPage list(ProductCriteria criteria);
}
