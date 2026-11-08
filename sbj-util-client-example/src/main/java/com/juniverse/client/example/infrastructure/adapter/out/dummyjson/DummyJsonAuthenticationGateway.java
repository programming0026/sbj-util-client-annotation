package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import com.juniverse.client.example.application.port.out.AuthenticationGateway;
import com.juniverse.client.example.domain.exception.InvalidCredentialsException;
import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.AuthTokens;
import com.juniverse.client.example.domain.model.Credentials;
import com.juniverse.client.example.domain.model.UserProfile;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api.AuthApi;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.LoginRequestDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.RefreshRequestDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Adaptador de salida: implementa la autenticación con el cliente declarativo {@link AuthApi}.
 *
 * Traduce los códigos de DummyJSON al significado de negocio: 400 al iniciar sesión son credenciales
 * inválidas; 400, 401 o 403 con un token son una sesión no autorizada.
 */
@Component
public class DummyJsonAuthenticationGateway implements AuthenticationGateway {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthApi authApi;
    private final DummyJsonMapper mapper;
    private final DummyJsonErrorTranslator errorTranslator;

    /**
     * Crea el adaptador.
     *
     * @param authApi el cliente declarativo generado por la librería
     * @param mapper el conversor entre DTOs y dominio
     * @param errorTranslator el traductor de errores técnicos
     */
    public DummyJsonAuthenticationGateway(AuthApi authApi, DummyJsonMapper mapper,
                                          DummyJsonErrorTranslator errorTranslator) {
        this.authApi = authApi;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
    }

    @Override
    public AuthSession authenticate(Credentials credentials) {
        try {
            return mapper.toDomain(authApi.login(
                    new LoginRequestDto(credentials.username(), credentials.password(), null)));
        } catch (HttpClientErrorException.BadRequest e) {
            throw new InvalidCredentialsException();
        } catch (RuntimeException e) {
            throw errorTranslator.translate("log in", e);
        }
    }

    @Override
    public UserProfile fetchProfile(String accessToken) {
        try {
            return mapper.toDomain(authApi.me(BEARER_PREFIX + accessToken));
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new UnauthorizedException("The access token is invalid or has expired");
        } catch (RuntimeException e) {
            throw errorTranslator.translate("get the current user", e);
        }
    }

    @Override
    public AuthTokens renew(String refreshToken) {
        try {
            return mapper.toDomain(authApi.refresh(new RefreshRequestDto(refreshToken, null)));
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized
                 | HttpClientErrorException.Forbidden e) {
            throw new UnauthorizedException("The refresh token is invalid or has expired");
        } catch (RuntimeException e) {
            throw errorTranslator.translate("refresh the session", e);
        }
    }
}
