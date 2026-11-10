package com.juniverse.client.example.application.service;

import com.juniverse.client.example.application.port.out.AuthenticationGateway;
import com.juniverse.client.example.domain.exception.UnauthorizedException;
import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.AuthTokens;
import com.juniverse.client.example.domain.model.Credentials;
import com.juniverse.client.example.domain.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Java Universe
 */
class AuthenticationServiceTest {

    private static final UserProfile USER = new UserProfile(1, "emilys", "e@x.com", "Emily", "Johnson", null, null);

    private final AuthenticationGateway gateway = mock(AuthenticationGateway.class);
    private final AuthenticationService service = new AuthenticationService(gateway);

    @Test
    void loginDelegatesToTheGateway() {
        Credentials credentials = new Credentials("emilys", "secret");
        AuthSession session = new AuthSession(USER, new AuthTokens("a", "r"));
        when(gateway.authenticate(credentials)).thenReturn(session);

        assertThat(service.login(credentials)).isSameAs(session);
    }

    @Test
    void currentUserTrimsTheTokenBeforeCallingTheGateway() {
        when(gateway.fetchProfile("abc")).thenReturn(USER);

        assertThat(service.getCurrentUser("  abc ")).isSameAs(USER);
    }

    @Test
    void refreshDelegatesToTheGateway() {
        AuthTokens tokens = new AuthTokens("a2", "r2");
        when(gateway.renew("r1")).thenReturn(tokens);

        assertThat(service.refresh("r1")).isSameAs(tokens);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingTokensAreRejectedWithoutCallingTheGateway(String token) {
        assertThatThrownBy(() -> service.getCurrentUser(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("The access token is missing");
        assertThatThrownBy(() -> service.refresh(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("The refresh token is missing");
        verifyNoInteractions(gateway);
    }
}
