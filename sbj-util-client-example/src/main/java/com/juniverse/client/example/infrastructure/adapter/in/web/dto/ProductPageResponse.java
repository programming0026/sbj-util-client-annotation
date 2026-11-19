package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.ProductPage;

import java.util.List;

/**
 * Page of products in the example's REST API.
 *
 * @param items the products of the page
 * @param total how many there are in total
 * @param skip how many were skipped
 * @param limit the page size
 * @param hasNext whether there are more pages
 * @author Java Universe
 */
public record ProductPageResponse(List<ProductResponse> items, long total, int skip, int limit, boolean hasNext) {

    /**
     * Converts a domain page.
     *
     * @param page the page
     * @return the response
     */
    public static ProductPageResponse from(ProductPage page) {
        return new ProductPageResponse(page.items().stream().map(ProductResponse::from).toList(), page.total(),
                page.skip(), page.limit(), page.hasNext());
    }
}
