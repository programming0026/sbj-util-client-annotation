package com.juniverse.client.example.application.service;

import com.juniverse.client.example.application.port.in.CreateProductUseCase;
import com.juniverse.client.example.application.port.in.DeleteProductUseCase;
import com.juniverse.client.example.application.port.in.UpdateProductUseCase;
import com.juniverse.client.example.application.port.out.ProductWriter;
import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductChanges;

import java.util.Objects;

/**
 * Use cases that modify the catalog (create, update and delete).
 *
 * Separate from {@link ProductQueryService} (single responsibility): queries and changes evolve
 * for different reasons and depend on different ports.
 *
 * @author Java Universe
 */
public class ProductCommandService implements CreateProductUseCase, UpdateProductUseCase, DeleteProductUseCase {

    private final ProductWriter productWriter;

    /**
     * Creates the service.
     *
     * @param productWriter the catalog writing port
     */
    public ProductCommandService(ProductWriter productWriter) {
        this.productWriter = Objects.requireNonNull(productWriter, "productWriter");
    }

    @Override
    public Product create(NewProduct newProduct) {
        return productWriter.create(Objects.requireNonNull(newProduct, "newProduct"));
    }

    @Override
    public Product update(long id, ProductChanges changes) {
        return productWriter.update(id, Objects.requireNonNull(changes, "changes"))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public Product delete(long id) {
        return productWriter.delete(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
