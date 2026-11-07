package com.juniverse.client.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientExceptionTest {

    @Test
    void messageConstructor() {
        ClientException ex = new ClientException("boom");

        assertThat(ex).hasMessage("boom").hasNoCause();
    }

    @Test
    void messageAndCauseConstructor() {
        IllegalStateException cause = new IllegalStateException("root");

        ClientException ex = new ClientException("boom", cause);

        assertThat(ex).hasMessage("boom").hasCause(cause);
    }

    @Test
    void causeConstructor() {
        IllegalStateException cause = new IllegalStateException("root");

        ClientException ex = new ClientException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }
}
