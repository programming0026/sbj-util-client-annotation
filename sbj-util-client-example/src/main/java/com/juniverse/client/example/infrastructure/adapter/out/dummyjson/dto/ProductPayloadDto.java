package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Cuerpo para crear o modificar un producto. Los campos nulos no se envían, lo que permite cambios
 * parciales.
 *
 * @param title nombre
 * @param description descripción
 * @param category categoría
 * @param price precio
 * @param stock unidades disponibles
 * @param brand marca
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductPayloadDto(String title, String description, String category, BigDecimal price, Integer stock,
                                String brand) {
}
