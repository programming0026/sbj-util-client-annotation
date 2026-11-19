package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

import java.util.Set;

/**
 * Which products are wanted and how: a page, optionally filtered by text <b>or</b> by category
 * (not both at once) and optionally sorted.
 *
 * @param page the requested page
 * @param searchText text to search, or null
 * @param category category (slug), or null
 * @param sortBy sort field, or null
 * @param order sort direction
 * @author Java Universe
 */
public record ProductCriteria(PageRequest page, String searchText, String category, String sortBy, SortOrder order) {

    /** Fields by which results can be sorted. */
    public static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "title", "price", "rating", "stock", "brand", "category", "discountPercentage");

    /**
     * Normalizes the optional values and validates the combinations.
     *
     * @throws InvalidRequestException if search and category are requested together or the sort field is unknown
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
     * All products, first page, unsorted.
     *
     * @return the default criteria
     */
    public static ProductCriteria all() {
        return new ProductCriteria(null, null, null, null, null);
    }

    /**
     * Indicates whether the search is by text.
     *
     * @return true if there is search text
     */
    public boolean isSearch() {
        return searchText != null;
    }

    /**
     * Indicates whether the filter is by category.
     *
     * @return true if there is a category
     */
    public boolean isByCategory() {
        return category != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
