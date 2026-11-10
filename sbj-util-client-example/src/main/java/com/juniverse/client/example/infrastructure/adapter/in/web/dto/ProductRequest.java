package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.ProductChanges;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Cuerpo para crear o modificar un producto en la API REST del ejemplo. Las reglas (nombre y precio
 * obligatorios al crear, al menos un campo al modificar) las valida el dominio.
 *
 * @param title nombre
 * @param description descripción
 * @param category categoría
 * @param price precio
 * @param stock unidades disponibles
 * @param brand marca
 * @author Java Universe
 */
public record ProductRequest(
        @Schema(description = "Nombre del producto (obligatorio al crear)", example = "Lámpara de escritorio") String title,
        @Schema(description = "Descripción", example = "Lámpara LED ajustable") String description,
        @Schema(description = "Slug de la categoría", example = "furniture") String category,
        @Schema(description = "Precio, 0 o más (obligatorio al crear)", example = "12.5") BigDecimal price,
        @Schema(description = "Unidades disponibles, 0 o más", example = "40") Integer stock,
        @Schema(description = "Marca", example = "Lumina") String brand) {

    /**
     * Convierte el cuerpo en un producto nuevo.
     *
     * @return el producto nuevo
     */
    public NewProduct toNewProduct() {
        return new NewProduct(title, description, category, price, stock, brand);
    }

    /**
     * Convierte el cuerpo en cambios parciales.
     *
     * @return los cambios
     */
    public ProductChanges toChanges() {
        return new ProductChanges(title, description, category, price, stock, brand);
    }
}
