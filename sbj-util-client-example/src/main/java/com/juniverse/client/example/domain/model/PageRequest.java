package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

/**
 * Requests a page of results.
 *
 * @param limit maximum number of elements (1 to {@value #MAX_LIMIT})
 * @param skip how many elements are skipped before the page (0 or more)
 * @author Java Universe
 */
public record PageRequest(int limit, int skip) {

    /** Default page size. */
    public static final int DEFAULT_LIMIT = 30;

    /** Maximum page size: avoids requesting whole catalogs by accident. */
    public static final int MAX_LIMIT = 100;

    /**
     * Validates the range of the values.
     *
     * @throws InvalidRequestException if any value is out of range
     */
    public PageRequest {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (skip < 0) {
            throw new InvalidRequestException("skip must not be negative");
        }
    }

    /**
     * First page with the default size.
     *
     * @return the first page
     */
    public static PageRequest first() {
        return new PageRequest(DEFAULT_LIMIT, 0);
    }
}
