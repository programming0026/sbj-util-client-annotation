package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo para iniciar sesión.
 *
 * @param username usuario
 * @param password contraseña
 * @param expiresInMins minutos de vida del token de acceso, o null para el valor por defecto del servicio
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginRequestDto(String username, String password, Integer expiresInMins) {

    /** La contraseña nunca debe aparecer en logs. */
    @Override
    public String toString() {
        return "LoginRequestDto[username=" + username + ", password=***]";
    }
}
