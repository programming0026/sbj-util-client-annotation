package com.juniverse.client.example.domain.model;

import java.util.List;

/**
 * A page of products.
 *
 * @param items the products of the page
 * @param total how many products there are in total
 * @param skip how many were skipped before this page
 * @param limit the requested page size
 * @author Java Universe
 */
public record ProductPage(List<Product> items, long total, int skip, int limit) {

    /** Copies the list so that the page is immutable. */
    public ProductPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * Indicates whether there are more pages after this one.
     *
     * @return true if there are products left to query
     */
    public boolean hasNext() {
        return (long) skip + items.size() < total;
    }
}
