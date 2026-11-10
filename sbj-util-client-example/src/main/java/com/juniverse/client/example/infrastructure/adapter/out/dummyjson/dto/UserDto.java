package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Usuario de DummyJSON (solo los campos que usa el ejemplo).
 *
 * Los números son tipos envoltorio a propósito: DummyJSON omite campos en algunas respuestas (por ejemplo
 * al crear o modificar) y Jackson 3 rechaza un campo primitivo ausente.
 *
 * @param id identificador
 * @param username usuario
 * @param email correo
 * @param firstName nombre
 * @param lastName apellido
 * @param gender género
 * @param image imagen de perfil
 * @author Java Universe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(Long id, String username, String email, String firstName, String lastName, String gender,
                      String image) {
}
