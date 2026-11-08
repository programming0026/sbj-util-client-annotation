package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de iniciar sesión: el usuario y sus tokens.
 *
 * Los números son tipos envoltorio a propósito: DummyJSON omite campos en algunas respuestas (por ejemplo
 * al crear o modificar) y Jackson 3 rechaza un campo primitivo ausente.
 *
 * @param id identificador del usuario
 * @param username usuario
 * @param email correo
 * @param firstName nombre
 * @param lastName apellido
 * @param gender género
 * @param image imagen de perfil
 * @param accessToken token de acceso
 * @param refreshToken token de renovación
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponseDto(Long id, String username, String email, String firstName, String lastName,
                               String gender, String image, String accessToken, String refreshToken) {

    /** Los tokens nunca deben aparecer en logs. */
    @Override
    public String toString() {
        return "LoginResponseDto[id=" + id + ", username=" + username + ", tokens=***]";
    }
}
