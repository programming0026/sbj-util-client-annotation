package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DummyJSON user (only the fields the example uses).
 *
 * The numbers are wrapper types on purpose: DummyJSON omits fields in some responses (for example
 * when creating or modifying) and Jackson 3 rejects a missing primitive field.
 *
 * @param id identifier
 * @param username user name
 * @param email email
 * @param firstName first name
 * @param lastName last name
 * @param gender gender
 * @param image profile image
 * @author Java Universe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(Long id, String username, String email, String firstName, String lastName, String gender,
                      String image) {
}
