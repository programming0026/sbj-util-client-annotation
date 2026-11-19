package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api;

import io.github.programming0026.juniverse.client.autoconfigure.CircuitBreakerConfig;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
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
 * Declarative client of https://dummyjson.com/docs/auth.
 *
 * Login and refresh are POST (non-idempotent): the library only retries them if the connection was not even
 * established, so as not to repeat an operation the server may have processed.
 *
 * @author Java Universe
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
     * Logs in.
     *
     * @param request user name and password
     * @return the user and their tokens
     */
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    LoginResponseDto login(@RequestBody LoginRequestDto request);

    /**
     * Gets the user who owns the token.
     *
     * @param authorization the complete header, for example {@code Bearer <token>}
     * @return the user
     */
    @GetMapping("/me")
    UserDto me(@RequestHeader("Authorization") String authorization);

    /**
     * Renews the tokens.
     *
     * @param request the refresh token
     * @return the new tokens
     */
    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    RefreshResponseDto refresh(@RequestBody RefreshRequestDto request);
}
