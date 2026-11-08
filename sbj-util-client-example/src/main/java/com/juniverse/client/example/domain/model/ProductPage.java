package com.juniverse.client.example.domain.model;

import java.util.List;

/**
 * Una página de productos.
 *
 * @param items los productos de la página
 * @param total cuántos productos hay en total
 * @param skip cuántos se omitieron antes de esta página
 * @param limit el tamaño de página pedido
 */
public record ProductPage(List<Product> items, long total, int skip, int limit) {

    /** Copia la lista para que la página sea inmutable. */
    public ProductPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * Indica si hay más páginas después de esta.
     *
     * @return true si quedan productos por consultar
     */
    public boolean hasNext() {
        return (long) skip + items.size() < total;
    }
}
