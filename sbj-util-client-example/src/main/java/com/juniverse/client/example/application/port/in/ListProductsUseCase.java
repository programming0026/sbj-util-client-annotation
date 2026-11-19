package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.ProductPage;

/**
 * Use case: query products (all, by text or by category), paginated and sorted.
 *
 * @author Java Universe
 */
public interface ListProductsUseCase {

    /**
     * Queries a page of products.
     *
     * @param criteria which products and how
     * @return the page of products
     */
    ProductPage list(ProductCriteria criteria);
}
