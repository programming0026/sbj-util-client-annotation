package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.model.ProductCategory;

import java.util.List;

/**
 * Use case: list the product categories.
 *
 * @author Java Universe
 */
public interface ListCategoriesUseCase {

    /**
     * Lists the categories.
     *
     * @return the categories
     */
    List<ProductCategory> listCategories();
}
