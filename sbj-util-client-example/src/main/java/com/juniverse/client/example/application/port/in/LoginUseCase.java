package com.juniverse.client.example.application.port.in;

import com.juniverse.client.example.domain.exception.InvalidCredentialsException;
import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.Credentials;

/**
 * Use case: log in.
 *
 * @author Java Universe
 */
public interface LoginUseCase {

    /**
     * Logs in.
     *
     * @param credentials user name and password
     * @return the user and their tokens
     * @throws InvalidCredentialsException if the data is not correct
     */
    AuthSession login(Credentials credentials);
}
