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
 * Casos de uso de consulta del catálogo.
 *
 * Depende solo del puerto {@link ProductReader} (inversión de dependencias): no sabe si los datos vienen
 * de una API REST, una base de datos o un archivo. Es una clase sin anotaciones de Spring; se registra
 * como bean en la configuración de la infraestructura.
 */
public class ProductQueryService implements ListProductsUseCase, GetProductUseCase, ListCategoriesUseCase {

    private final ProductReader productReader;

    /**
     * Crea el servicio.
     *
     * @param productReader el puerto de lectura del catálogo
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
