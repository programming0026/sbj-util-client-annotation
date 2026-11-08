package com.juniverse.client.example.application.port.out;

import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductCategory;
import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.ProductPage;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: lectura del catálogo de productos.
 *
 * Está separado de {@link ProductWriter} (segregación de interfaces): quien solo consulta no depende de
 * operaciones de escritura.
 */
public interface ProductReader {

    /**
     * Busca productos.
     *
     * @param criteria qué productos y cómo
     * @return la página de productos
     * @throws ExternalServiceException si el catálogo no está disponible
     */
    ProductPage findAll(ProductCriteria criteria);

    /**
     * Busca un producto por identificador.
     *
     * @param id el identificador
     * @return el producto, o vacío si no existe
     * @throws ExternalServiceException si el catálogo no está disponible
     */
    Optional<Product> findById(long id);

    /**
     * Lista las categorías.
     *
     * @return las categorías
     * @throws ExternalServiceException si el catálogo no está disponible
     */
    List<ProductCategory> findCategories();
}
