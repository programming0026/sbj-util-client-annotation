package com.juniverse.client.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import javax.net.ssl.SSLContext;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SSLUtilTest {

    static final String KEYSTORE = "classpath:test-keystore.p12";

    static final String PASSWORD = "changeit";

    private static final String PROFILE_PROPERTY = "spring.profiles.active";

    private final SSLUtil sslUtil = new SSLUtil();

    private String previousProfile;

    @BeforeEach
    void saveProfile() {
        previousProfile = System.getProperty(PROFILE_PROPERTY);
    }

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) {
            System.clearProperty(PROFILE_PROPERTY);
        } else {
            System.setProperty(PROFILE_PROPERTY, previousProfile);
        }
    }

    @Test
    void createSSLContextWithKeystoreAndTruststoreFromClasspath() throws Exception {
        SSLContext context = sslUtil.createSSLContext(KEYSTORE, PASSWORD, KEYSTORE, PASSWORD);

        assertThat(context.getProtocol()).isEqualTo("TLS");
        assertThat(context.createSSLEngine()).isNotNull();
    }

    @Test
    void createSSLContextWithoutStores() throws Exception {
        assertThat(sslUtil.createSSLContext(null, null, null, null)).isNotNull();
        assertThat(sslUtil.createSSLContext("", "", "", "")).isNotNull();
    }

    @Test
    void createSSLContextFromFileSystemPath(@TempDir Path tempDir) throws Exception {
        Path pfx = tempDir.resolve("store.pfx");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("test-keystore.p12")) {
            Files.copy(in, pfx);
        }

        assertThat(sslUtil.createSSLContext(pfx.toString(), PASSWORD, null, null)).isNotNull();
    }

    @Test
    void createSSLContextFailsForMissingClasspathResource() {
        assertThatThrownBy(() -> sslUtil.createSSLContext("classpath:missing.p12", PASSWORD, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing.p12");
    }

    @Test
    void createSSLContextFailsForMissingFile() {
        assertThatThrownBy(() -> sslUtil.createSSLContext(null, null, "does-not-exist.jks", PASSWORD))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void insecureContextRejectedOutsideDevelopment() {
        assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null);
        System.setProperty(PROFILE_PROPERTY, "prod");

        assertThatThrownBy(sslUtil::createInsecureSSLContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void insecureContextRejectedWhenNoProfile() {
        assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null);
        System.clearProperty(PROFILE_PROPERTY);

        assertThatThrownBy(sslUtil::createInsecureSSLContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Active profiles: []");
    }

    @Test
    void insecureContextAllowedInDevelopmentProfiles() throws Exception {
        assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null);
        for (String profile : new String[]{"dev", "LOCAL", "development"}) {
            System.setProperty(PROFILE_PROPERTY, profile);
            assertThat(sslUtil.createInsecureSSLContext()).isNotNull();
        }
    }

    @Test
    void insecureContextAllowedWhenOneOfSeveralProfilesIsDevelopment() throws Exception {
        assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null);
        System.setProperty(PROFILE_PROPERTY, "kafka, dev");

        assertThat(sslUtil.createInsecureSSLContext()).isNotNull();
    }

    @Test
    void springEnvironmentProfilesAreUsed() throws Exception {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("metrics", "local");
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThat(new SSLUtil(local).createInsecureSSLContext()).isNotNull();
        assertThatThrownBy(() -> new SSLUtil(prod).createInsecureSSLContext())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Active profiles: [prod]");
    }

}
