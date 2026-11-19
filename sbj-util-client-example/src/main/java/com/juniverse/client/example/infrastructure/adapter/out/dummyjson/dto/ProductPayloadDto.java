package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Body to create or modify a product. Null fields are not sent, which allows partial
 * changes.
 *
 * @param title name
 * @param description description
 * @param category category
 * @param price price
 * @param stock available units
 * @param brand brand
 * @author Java Universe
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductPayloadDto(String title, String description, String category, BigDecimal price, Integer stock,
                                String brand) {
}
