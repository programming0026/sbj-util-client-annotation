package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.Credentials;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo para iniciar sesión en la API REST del ejemplo.
 *
 * @param username usuario
 * @param password contraseña
 * @author Java Universe
 */
public record LoginRequest(
        @Schema(description = "Nombre de usuario", example = "emilys") String username,
        @Schema(description = "Contraseña", example = "emilyspass", format = "password") String password) {

    /**
     * Convierte el cuerpo en credenciales del dominio.
     *
     * @return las credenciales
     */
    public Credentials toCredentials() {
        return new Credentials(username, password);
    }

    /** La contraseña nunca debe aparecer en logs. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
