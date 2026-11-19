package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Page of DummyJSON products.
 *
 * The numbers are wrapper types on purpose: DummyJSON omits fields in some responses (for example
 * when creating or modifying) and Jackson 3 rejects a missing primitive field.
 *
 * @param products the products of the page
 * @param total how many there are in total
 * @param skip how many were skipped
 * @param limit the page size
 * @author Java Universe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductListDto(List<ProductDto> products, Long total, Integer skip, Integer limit) {
}
