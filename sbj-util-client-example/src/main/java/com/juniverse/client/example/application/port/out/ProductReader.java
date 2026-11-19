package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductCategory;
import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.ProductPage;

import java.util.List;
import java.util.Optional;

/**
 * Output port: reading of the product catalog.
 *
 * It is separate from {@link ProductWriter} (interface segregation): whoever only queries does not depend on
 * write operations.
 *
 * @author Java Universe
 */
public interface ProductReader {

    /**
     * Searches for products.
     *
     * @param criteria which products and how
     * @return the page of products
     * @throws ExternalServiceException if the catalog is unavailable
     */
    ProductPage findAll(ProductCriteria criteria);

    /**
     * Finds a product by identifier.
     *
     * @param id the identifier
     * @return the product, or empty if it does not exist
     * @throws ExternalServiceException if the catalog is unavailable
     */
    Optional<Product> findById(long id);

    /**
     * Lists the categories.
     *
     * @return the categories
     * @throws ExternalServiceException if the catalog is unavailable
     */
    List<ProductCategory> findCategories();
}
