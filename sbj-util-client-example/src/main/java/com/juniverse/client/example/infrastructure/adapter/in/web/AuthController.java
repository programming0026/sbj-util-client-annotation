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
@Tag(name = "Authentication", description = "Log in, current user and token renewal")
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
    @Operation(summary = "Log in",
            description = "Try it with the DummyJSON test user: `emilys` / `emilyspass`.")
    @ApiResponse(responseCode = "200", description = "The user and the session tokens")
    @ApiResponse(responseCode = "400", description = "The user name or the password is missing")
    @ApiResponse(responseCode = "401", description = "Wrong user name or password")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
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
    @Operation(summary = "Get the current user",
            description = "Returns the user who owns the access token sent as `Authorization: Bearer <token>`.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "401", description = "The token is missing, invalid or expired")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
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
    @Operation(summary = "Renew the tokens", description = "Exchanges a refresh token for a new pair of tokens.")
    @ApiResponse(responseCode = "200", description = "The new tokens")
    @ApiResponse(responseCode = "401", description = "The refresh token is missing, invalid or expired")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
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
