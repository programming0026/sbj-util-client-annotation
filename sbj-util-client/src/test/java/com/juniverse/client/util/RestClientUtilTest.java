package com.juniverse.client.util;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.SSLConfig;
import com.juniverse.client.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RestClientUtilTest {

    @RestHttpClient(url = "http://localhost:8080")
    interface DefaultClient {
    }

    @RestHttpClient(url = "https://localhost:8443", connectionTimeToLive = 60000,
            ssl = @SSLConfig(keystorePath = SSLUtilTest.KEYSTORE, keystorePassword = SSLUtilTest.PASSWORD,
                    truststorePath = SSLUtilTest.KEYSTORE, truststorePassword = SSLUtilTest.PASSWORD))
    interface KeystoreClient {
    }

    @RestHttpClient(url = "https://localhost:8443", ssl = @SSLConfig(truststorePath = SSLUtilTest.KEYSTORE,
            truststorePassword = SSLUtilTest.PASSWORD))
    interface TruststoreOnlyClient {
    }

    @RestHttpClient(url = "https://localhost:8443", ssl = @SSLConfig(enabled = false))
    interface InsecureClient {
    }

    @RestHttpClient(url = "https://localhost:8443", ssl = @SSLConfig(keystorePath = "missing-keystore.jks",
            keystorePassword = "x"))
    interface BrokenClient {
    }

    private final RestClientUtil util = new RestClientUtil(new SSLUtil());

    private String previousProfile;

    @BeforeEach
    void saveProfile() {
        previousProfile = System.getProperty("spring.profiles.active");
    }

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) {
            System.clearProperty("spring.profiles.active");
        } else {
            System.setProperty("spring.profiles.active", previousProfile);
        }
    }

    private static RestHttpClient config(Class<?> type) {
        return type.getAnnotation(RestHttpClient.class);
    }

    @Test
    void configureRestClientWithDefaults() {
        RestClient client = util.configureRestClient(config(DefaultClient.class));

        assertThat(client).isNotNull();
    }

    @Test
    void configureRestTemplateUsesHttpComponents() {
        RestTemplate template = util.configureRestTemplate(config(DefaultClient.class));

        assertThat(template.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void configureWithKeystoreAndTimeToLive() {
        assertThat(util.configureRestClient(config(KeystoreClient.class))).isNotNull();
        assertThat(util.configureRestTemplate(config(KeystoreClient.class))).isNotNull();
    }

    @Test
    void configureWithTruststoreOnly() {
        assertThat(util.configureRestClient(config(TruststoreOnlyClient.class))).isNotNull();
    }

    @Test
    void configureInsecureInDevelopmentProfile() {
        assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null);
        System.setProperty("spring.profiles.active", "dev");

        assertThat(util.configureRestClient(config(InsecureClient.class))).isNotNull();
        assertThat(util.configureRestTemplate(config(InsecureClient.class))).isNotNull();
    }

    @Test
    void configureRestClientWrapsSslErrors() {
        assertThatThrownBy(() -> util.configureRestClient(config(BrokenClient.class)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("RestClient");
    }

    @Test
    void configureRestTemplateWrapsSslErrors() {
        assertThatThrownBy(() -> util.configureRestTemplate(config(BrokenClient.class)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("RestTemplate");
    }
}
