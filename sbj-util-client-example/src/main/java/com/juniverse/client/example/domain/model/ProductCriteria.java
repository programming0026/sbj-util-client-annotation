package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Set;

/**
 * Qué productos se quieren y cómo: una página, opcionalmente filtrada por texto <b>o</b> por categoría
 * (no ambas a la vez) y opcionalmente ordenada.
 *
 * @param page la página pedida
 * @param searchText texto a buscar, o null
 * @param category categoría (slug), o null
 * @param sortBy campo de ordenamiento, o null
 * @param order sentido del ordenamiento
 * @author Java Universe
 */
public record ProductCriteria(PageRequest page, String searchText, String category, String sortBy, SortOrder order) {

    /** Campos por los que se puede ordenar. */
    public static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "title", "price", "rating", "stock", "brand", "category", "discountPercentage");

    /**
     * Normaliza los valores opcionales y valida las combinaciones.
     *
     * @throws InvalidRequestException si se pide búsqueda y categoría juntas o un campo de orden desconocido
     */
    public ProductCriteria {
        page = page == null ? PageRequest.first() : page;
        searchText = blankToNull(searchText);
        category = blankToNull(category);
        sortBy = blankToNull(sortBy);
        order = order == null ? SortOrder.ASC : order;
        if (searchText != null && category != null) {
            throw new InvalidRequestException("searchText and category cannot be combined");
        }
        if (sortBy != null && !SORTABLE_FIELDS.contains(sortBy)) {
            throw new InvalidRequestException("sortBy must be one of " + SORTABLE_FIELDS + " but was '" + sortBy + "'");
        }
    }

    /**
     * Todos los productos, primera página, sin orden.
     *
     * @return los criterios por defecto
     */
    public static ProductCriteria all() {
        return new ProductCriteria(null, null, null, null, null);
    }

    /**
     * Indica si se busca por texto.
     *
     * @return true si hay texto de búsqueda
     */
    public boolean isSearch() {
        return searchText != null;
    }

    /**
     * Indica si se filtra por categoría.
     *
     * @return true si hay categoría
     */
    public boolean isByCategory() {
        return category != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
