package com.juniverse.client.example.application.service;

import com.juniverse.client.example.application.port.in.GetProductUseCase;
import com.juniverse.client.example.application.port.in.ListCategoriesUseCase;
import com.juniverse.client.example.application.port.in.ListProductsUseCase;
import com.juniverse.client.example.application.port.out.ProductReader;
import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductCategory;
import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.ProductPage;

import java.util.List;
import java.util.Objects;

/**
 * Catalog query use cases.
 *
 * It depends only on the {@link ProductReader} port (dependency inversion): it does not know whether the data
 * comes from a REST API, a database or a file. It is a class without Spring annotations; it is registered
 * as a bean in the infrastructure configuration.
 *
 * @author Java Universe
 */
public class ProductQueryService implements ListProductsUseCase, GetProductUseCase, ListCategoriesUseCase {

    private final ProductReader productReader;

    /**
     * Creates the service.
     *
     * @param productReader the catalog reading port
     */
    public ProductQueryService(ProductReader productReader) {
        this.productReader = Objects.requireNonNull(productReader, "productReader");
    }

    @Override
    public ProductPage list(ProductCriteria criteria) {
        return productReader.findAll(criteria == null ? ProductCriteria.all() : criteria);
    }

    @Override
    public Product getById(long id) {
        return productReader.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public List<ProductCategory> listCategories() {
        return productReader.findCategories();
    }
}
