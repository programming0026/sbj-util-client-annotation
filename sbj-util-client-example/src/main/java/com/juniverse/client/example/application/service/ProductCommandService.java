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
 * Casos de uso que modifican el catálogo (crear, actualizar y eliminar).
 *
 * Separado de {@link ProductQueryService} (responsabilidad única): las consultas y los cambios evolucionan
 * por razones distintas y dependen de puertos distintos.
 */
public class ProductCommandService implements CreateProductUseCase, UpdateProductUseCase, DeleteProductUseCase {

    private final ProductWriter productWriter;

    /**
     * Crea el servicio.
     *
     * @param productWriter el puerto de escritura del catálogo
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
