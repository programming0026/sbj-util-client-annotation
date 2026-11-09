package io.github.programming0026.juniverse.client.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los patrones de {@code nonProxyHosts}. */
class NonProxyHostsRoutePlannerTest {

    @Test
    void anExactNameMatchesIgnoringCase() {
        assertThat(NonProxyHostsRoutePlanner.matches("LocalHost", List.of("localhost"))).isTrue();
        assertThat(NonProxyHostsRoutePlanner.matches("localhost2", List.of("localhost"))).isFalse();
    }

    @Test
    void aLeadingWildcardMatchesTheSuffix() {
        List<String> patterns = List.of("*.interno.com");

        assertThat(NonProxyHostsRoutePlanner.matches("api.interno.com", patterns)).isTrue();
        assertThat(NonProxyHostsRoutePlanner.matches("interno.com", patterns)).isFalse();
        assertThat(NonProxyHostsRoutePlanner.matches("api.interno.com.evil.org", patterns)).isFalse();
    }

    @Test
    void aTrailingWildcardMatchesThePrefix() {
        List<String> patterns = List.of("10.0.*");

        assertThat(NonProxyHostsRoutePlanner.matches("10.0.3.7", patterns)).isTrue();
        assertThat(NonProxyHostsRoutePlanner.matches("10.1.0.1", patterns)).isFalse();
    }

    @Test
    void aLoneWildcardIsTreatedAsAnExactName() {
        assertThat(NonProxyHostsRoutePlanner.matches("anything", List.of("*"))).isFalse();
    }

    @Test
    void noPatternsMatchNothing() {
        assertThat(NonProxyHostsRoutePlanner.matches("localhost", List.of())).isFalse();
    }
}
