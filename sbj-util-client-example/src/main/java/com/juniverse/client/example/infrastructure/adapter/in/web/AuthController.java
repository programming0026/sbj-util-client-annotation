package com.juniverse.client.example.infrastructure.adapter.in.web;

import com.juniverse.client.example.application.port.in.GetCurrentUserUseCase;
import com.juniverse.client.example.application.port.in.LoginUseCase;
import com.juniverse.client.example.application.port.in.RefreshSessionUseCase;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.LoginRequest;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.RefreshRequest;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.SessionResponse;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.TokensResponse;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.UserResponse;
import com.juniverse.client.example.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Input adapter: authentication (log in, current user and token renewal).
 *
 * @author Java Universe
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión, usuario actual y renovación de tokens")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LoginUseCase login;
    private final GetCurrentUserUseCase getCurrentUser;
    private final RefreshSessionUseCase refreshSession;

    /**
     * Creates the controller.
     *
     * @param login login use case
     * @param getCurrentUser current user use case
     * @param refreshSession token renewal use case
     */
    public AuthController(LoginUseCase login, GetCurrentUserUseCase getCurrentUser,
                          RefreshSessionUseCase refreshSession) {
        this.login = login;
        this.getCurrentUser = getCurrentUser;
        this.refreshSession = refreshSession;
    }

    /**
     * Logs in.
     *
     * @param request user name and password
     * @return the user and their tokens
     */
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Pruébalo con el usuario de prueba de DummyJSON: `emilys` / `emilyspass`.")
    @ApiResponse(responseCode = "200", description = "El usuario y los tokens de la sesión")
    @ApiResponse(responseCode = "400", description = "Falta el usuario o la contraseña")
    @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public SessionResponse login(@RequestBody LoginRequest request) {
        return SessionResponse.from(login.login(request.toCredentials()));
    }

    /**
     * Gets the user who owns the token of the {@code Authorization: Bearer <token>} header.
     *
     * @param authorization the {@code Authorization} header
     * @return the user
     */
    @GetMapping("/me")
    @Operation(summary = "Consultar el usuario actual",
            description = "Devuelve el usuario dueño del token de acceso enviado como `Authorization: Bearer <token>`.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "El usuario")
    @ApiResponse(responseCode = "401", description = "El token falta, es inválido o expiró")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public UserResponse me(@Parameter(hidden = true)
                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        return UserResponse.from(getCurrentUser.getCurrentUser(bearerToken(authorization)));
    }

    /**
     * Renews the tokens.
     *
     * @param request the refresh token
     * @return the new tokens
     */
    @PostMapping("/refresh")
    @Operation(summary = "Renovar los tokens", description = "Cambia un token de renovación por un par de tokens nuevo.")
    @ApiResponse(responseCode = "200", description = "Los tokens nuevos")
    @ApiResponse(responseCode = "401", description = "El token de renovación falta, es inválido o expiró")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public TokensResponse refresh(@RequestBody RefreshRequest request) {
        return TokensResponse.from(refreshSession.refresh(request.refreshToken()));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
