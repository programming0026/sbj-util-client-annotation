package com.juniverse.client.example.infrastructure.adapter.in.web.dto;

import com.juniverse.client.example.domain.model.UserProfile;

/**
 * Usuario en la API REST del ejemplo.
 *
 * @param id identificador
 * @param username usuario
 * @param email correo
 * @param firstName nombre
 * @param lastName apellido
 * @param gender género
 * @param image imagen de perfil
 */
public record UserResponse(long id, String username, String email, String firstName, String lastName,
                           String gender, String image) {

    /**
     * Convierte un usuario del dominio.
     *
     * @param user el usuario
     * @return la respuesta
     */
    public static UserResponse from(UserProfile user) {
        return new UserResponse(user.id(), user.username(), user.email(), user.firstName(), user.lastName(),
                user.gender(), user.image());
    }
}
