package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DummyJSON category.
 *
 * @param slug identifier
 * @param name display name
 * @param url category URL (not used)
 * @author Java Universe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryDto(String slug, String name, String url) {
}
