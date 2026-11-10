package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;

/**
 * Usuario y contraseña para iniciar sesión.
 *
 * @param username el usuario
 * @param password la contraseña
 * @author Java Universe
 */
public record Credentials(String username, String password) {

    /**
     * Valida que ambos datos vengan.
     *
     * @throws InvalidRequestException si falta el usuario o la contraseña
     */
    public Credentials {
        if (username == null || username.isBlank()) {
            throw new InvalidRequestException("username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new InvalidRequestException("password is required");
        }
        username = username.trim();
    }

    /** La contraseña nunca debe aparecer en logs ni en mensajes de error. */
    @Override
    public String toString() {
        return "Credentials[username=" + username + ", password=***]";
    }
}
