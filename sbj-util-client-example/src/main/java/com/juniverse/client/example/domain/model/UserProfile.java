package com.juniverse.client.example.domain.model;

/**
 * Datos públicos de un usuario.
 *
 * @param id identificador
 * @param username usuario
 * @param email correo
 * @param firstName nombre
 * @param lastName apellido
 * @param gender género, o null
 * @param image URL de la imagen de perfil, o null
 * @author Java Universe
 */
public record UserProfile(long id, String username, String email, String firstName, String lastName, String gender,
                          String image) {
}
