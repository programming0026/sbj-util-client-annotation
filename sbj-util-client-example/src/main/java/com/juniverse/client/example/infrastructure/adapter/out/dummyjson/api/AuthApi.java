package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api;

import com.juniverse.client.autoconfigure.CircuitBreakerConfig;
import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.LoggingInterceptor;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.LoginRequestDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.LoginResponseDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.RefreshRequestDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.RefreshResponseDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.UserDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente declarativo de https://dummyjson.com/docs/auth.
 *
 * Login y refresh son POST (no idempotentes): la librería solo los reintenta si la conexión ni siquiera se
 * llegó a establecer, para no repetir una operación que el servidor pudo haber procesado.
 */
@RestHttpClient(
        name = "dummyJsonAuth",
        url = "${dummyjson.base-url}/auth",
        connectTimeout = 3000,
        readTimeout = 10000,
        retry = @RetryConfig(maxRetries = 2, retryDelay = 300),
        circuitBreaker = @CircuitBreakerConfig(enabled = true, slidingWindowSize = 20, minimumNumberOfCalls = 10,
                failureRateThreshold = 50, waitDurationInOpenState = 30000),
        interceptors = LoggingInterceptor.class)
public interface AuthApi {

    /**
     * Inicia sesión.
     *
     * @param request usuario y contraseña
     * @return el usuario y sus tokens
     */
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    LoginResponseDto login(@RequestBody LoginRequestDto request);

    /**
     * Obtiene el usuario dueño del token.
     *
     * @param authorization el header completo, por ejemplo {@code Bearer <token>}
     * @return el usuario
     */
    @GetMapping("/me")
    UserDto me(@RequestHeader("Authorization") String authorization);

    /**
     * Renueva los tokens.
     *
     * @param request el token de renovación
     * @return los tokens nuevos
     */
    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    RefreshResponseDto refresh(@RequestBody RefreshRequestDto request);
}
