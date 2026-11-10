package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo para renovar los tokens.
 *
 * @param refreshToken token de renovación
 * @param expiresInMins minutos de vida del nuevo token de acceso, o null para el valor por defecto
 * @author Java Universe
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefreshRequestDto(String refreshToken, Integer expiresInMins) {

    /** El token nunca debe aparecer en logs. */
    @Override
    public String toString() {
        return "RefreshRequestDto[refreshToken=***]";
    }
}
