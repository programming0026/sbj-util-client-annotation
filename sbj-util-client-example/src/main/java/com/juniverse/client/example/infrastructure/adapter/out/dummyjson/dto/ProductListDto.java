package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Página de productos de DummyJSON.
 *
 * Los números son tipos envoltorio a propósito: DummyJSON omite campos en algunas respuestas (por ejemplo
 * al crear o modificar) y Jackson 3 rechaza un campo primitivo ausente.
 *
 * @param products los productos de la página
 * @param total cuántos hay en total
 * @param skip cuántos se omitieron
 * @param limit el tamaño de página
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductListDto(List<ProductDto> products, Long total, Integer skip, Integer limit) {
}
