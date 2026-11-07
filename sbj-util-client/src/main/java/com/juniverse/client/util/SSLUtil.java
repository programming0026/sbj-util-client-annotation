package com.juniverse.client.util;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The Class SSLConfiguration.
 */
public class SSLUtil {
    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(SSLUtil.class);

    /**
     * Creates the SSL context.
     *
     * @param keystorePath the keystore path
     * @param keystorePassword the keystore password
     * @param truststorePath the truststore path
     * @param truststorePassword the truststore password
     * @return the SSL context
     * @throws NoSuchAlgorithmException the exception
     * @throws java.security.UnrecoverableKeyException the exception
     * @throws java.security.cert.CertificateException the exception
     * @throws java.security.KeyStoreException the exception
     * @throws java.io.IOException the exception
     * @throws java.security.KeyManagementException the exception
     */
    public SSLContext createSSLContext(String keystorePath, String keystorePassword,
            String truststorePath, String truststorePassword)
            throws NoSuchAlgorithmException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, KeyManagementException {

        SSLContext sslContext = SSLContext.getInstance("TLS");

        KeyManager[] keyManagers = null;
        if (keystorePath != null && !keystorePath.isEmpty()) {
            keyManagers = createKeyManagers(keystorePath, keystorePassword);
        }

        TrustManager[] trustManagers = null;
        if (truststorePath != null && !truststorePath.isEmpty()) {
            trustManagers = createTrustManagers(truststorePath, truststorePassword);
        }

        sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());

        return sslContext;
    }

    /**
     * Creates the insecure SSL context.
     * WARNING: This method disables all SSL/TLS certificate validation and
     * should NEVER be used in production environments. It makes the application
     * vulnerable to Man-in-the-Middle (MITM) attacks.
     * This method is restricted to development and local environments only. Set
     * SPRING_PROFILES_ACTIVE=dev or local to enable.
     *
     * @return the SSL context
     * @throws NoSuchAlgorithmException the exception
     * @throws java.security.KeyManagementException the exception
     * @throws IllegalStateException if not in development environment
     */
    public SSLContext createInsecureSSLContext()
            throws NoSuchAlgorithmException, KeyManagementException {

        if (!isDevelopmentEnvironment()) {
            throw new IllegalStateException(
                    """
                Insecure SSL is only allowed in development environments.
                Current profile: %s"
                Set SPRING_PROFILES_ACTIVE=dev or local to enable insecure SSL.
                """.formatted(getCurrentProfile())
            );
        }

        LOG.warn("Environment: {}", getCurrentProfile());
        SSLContext sslContext = SSLContext.getInstance("TLS");

        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    LOG.debug("Insecure SSL: Skipping client certificate validation");
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    LOG.debug("Insecure SSL: Skipping server certificate validation");
                }
            }
        };

        sslContext.init(null, trustAllCerts, new SecureRandom());

        return sslContext;
    }

    /**
     * Checks if current environment is development.
     *
     * @return true if dev or local environment
     */
    private static boolean isDevelopmentEnvironment() {
        String profile = getCurrentProfile();
        return "dev".equalsIgnoreCase(profile)
                || "local".equalsIgnoreCase(profile)
                || "development".equalsIgnoreCase(profile);
    }

    /**
     * Gets current Spring profile.
     *
     * @return current profile or "unknown"
     */
    private static String getCurrentProfile() {
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile == null || profile.isEmpty()) {
            profile = System.getProperty("spring.profiles.active");
        }
        return profile != null ? profile : "unknown";
    }

    /**
     * Creates the key managers.
     *
     * @param keystorePath the keystore path
     * @param password the password
     * @return the key manager[]
     * @throws KeyStoreException the exception
     */
    private static KeyManager[] createKeyManagers(String keystorePath, String password)
            throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance(getKeystoreType(keystorePath));

        try (InputStream keystoreStream = getResourceStream(keystorePath)) {
            keyStore.load(keystoreStream, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        return kmf.getKeyManagers();
    }

    /**
     * Creates the trust managers.
     *
     * @param truststorePath the truststore path
     * @param password the password
     * @return the trust manager[]
     * @throws KeyStoreException the exception
     */
    private static TrustManager[] createTrustManagers(String truststorePath, String password)
            throws KeyStoreException, IOException,
            CertificateException, NoSuchAlgorithmException {
        KeyStore trustStore = KeyStore.getInstance(getKeystoreType(truststorePath));

        try (InputStream truststoreStream = getResourceStream(truststorePath)) {
            trustStore.load(truststoreStream, password.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        return tmf.getTrustManagers();
    }

    /**
     * Gets the keystore type.
     *
     * @param path the path
     * @return the keystore type
     */
    private static String getKeystoreType(String path) {
        if (path.endsWith(".p12") || path.endsWith(".pfx")) {
            return "PKCS12";
        }
        return "JKS";
    }

    /**
     * Gets the resource stream.
     *
     * @param path the path
     * @return the resource stream
     * @throws IOException the exception
     */
    private static InputStream getResourceStream(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            InputStream stream = SSLUtil.class.getClassLoader()
                    .getResourceAsStream(resourcePath);
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return stream;
        }
        return new FileInputStream(path);
    }
}
