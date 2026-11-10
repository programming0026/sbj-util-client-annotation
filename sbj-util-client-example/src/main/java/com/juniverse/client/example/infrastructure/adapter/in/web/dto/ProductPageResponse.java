package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.ProductPage;

import java.util.List;

/**
 * Página de productos en la API REST del ejemplo.
 *
 * @param items los productos de la página
 * @param total cuántos hay en total
 * @param skip cuántos se omitieron
 * @param limit el tamaño de página
 * @param hasNext si hay más páginas
 * @author Java Universe
 */
public record ProductPageResponse(List<ProductResponse> items, long total, int skip, int limit, boolean hasNext) {

    /**
     * Convierte una página del dominio.
     *
     * @param page la página
     * @return la respuesta
     */
    public static ProductPageResponse from(ProductPage page) {
        return new ProductPageResponse(page.items().stream().map(ProductResponse::from).toList(), page.total(),
                page.skip(), page.limit(), page.hasNext());
    }
}
