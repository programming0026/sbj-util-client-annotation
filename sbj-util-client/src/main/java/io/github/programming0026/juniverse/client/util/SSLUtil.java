package io.github.programming0026.juniverse.client.util;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
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
import java.util.Arrays;
import java.util.function.Supplier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
/**
 * Creates SSL contexts from a keystore and/or truststore, or an insecure context for development only.
 *
 * @author Java Universe
 */
public class SSLUtil {
    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(SSLUtil.class);

    /** Supplier of the environment from which the active profiles are read. */
    private final Supplier<Environment> environment;

    /**
     * Creates the utility without a Spring context: the profiles are read on each call from
     * {@code spring.profiles.active} (system property) or {@code SPRING_PROFILES_ACTIVE} (environment
     * variable).
     */
    public SSLUtil() {
        this.environment = StandardEnvironment::new;
    }

    /**
     * Creates the utility using the active profiles of the Spring {@link Environment} (including those defined
     * in {@code application.properties}).
     *
     * @param environment the Spring environment
     */
    public SSLUtil(Environment environment) {
        this.environment = () -> environment;
    }

    /**
     * Creates the SSL context. The paths accept the {@code classpath:} prefix; the store type is deduced
     * from the extension ({@code .p12}/{@code .pfx} is PKCS12, any other is JKS). An empty or
     * {@code null} path uses the JVM defaults for that part.
     *
     * @param keystorePath the keystore path
     * @param keystorePassword the keystore password
     * @param truststorePath the truststore path
     * @param truststorePassword the truststore password
     * @return the SSL context
     * @throws NoSuchAlgorithmException if the TLS algorithm is not available
     * @throws java.security.UnrecoverableKeyException if the key cannot be recovered from the keystore
     * @throws java.security.cert.CertificateException if a certificate cannot be loaded
     * @throws java.security.KeyStoreException if the store is not valid
     * @throws java.io.IOException if the file cannot be read
     * @throws java.security.KeyManagementException if the context initialization fails
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
     * Creates an insecure SSL context.
     * WARNING: it disables all SSL/TLS certificate validation and must NEVER be used in production;
     * it leaves the application exposed to Man-in-the-Middle (MITM) attacks.
     * It is only allowed in development environments: one of the active profiles must be dev, local or
     * development (case-insensitive; several profiles are supported, for example {@code dev,kafka}).
     *
     * @return the SSL context
     * @throws NoSuchAlgorithmException if the TLS algorithm is not available
     * @throws java.security.KeyManagementException if the context initialization fails
     * @throws IllegalStateException if it is not a development environment
     */
    public SSLContext createInsecureSSLContext()
			throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {

        String[] activeProfiles = environment.get().getActiveProfiles();
        if (!isDevelopmentEnvironment(activeProfiles)) {
            throw new IllegalStateException(
                    """
                Insecure SSL is only allowed in development environments.
                Active profiles: %s
                Activate the dev, local or development profile to enable insecure SSL.
                """.formatted(Arrays.toString(activeProfiles))
            );
        }

        if (LOG.isWarnEnabled()) {
            LOG.warn("Insecure SSL enabled, active profiles: {}", Arrays.toString(activeProfiles));
        }

        return SSLContextBuilder.create()
                .setProtocol("TLS")
                .setSecureRandom(new SecureRandom())
                .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                .build();
    }

    /**
     * Indicates whether any of the active profiles is a development one.
     *
     * @param activeProfiles the active profiles
     * @return true if any is dev, local or development
     */
    private static boolean isDevelopmentEnvironment(String[] activeProfiles) {
        return Arrays.stream(activeProfiles)
                .map(String::trim)
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile)
                        || "local".equalsIgnoreCase(profile)
                        || "development".equalsIgnoreCase(profile));
    }

    /**
     * Creates the key managers from the keystore.
     *
     * @param keystorePath the keystore path
     * @param password the password
     * @return the key managers
     * @throws KeyStoreException if the keystore is not valid
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
     * Creates the trust managers from the truststore.
     *
     * @param truststorePath the truststore path
     * @param password the password
     * @return the trust managers
     * @throws KeyStoreException if the truststore is not valid
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
     * Deduces the store type from the file extension.
     *
     * @param path the path
     * @return PKCS12 for '.p12/.pfx', JKS otherwise
     */
    private static String getKeystoreType(String path) {
        if (path.endsWith(".p12") || path.endsWith(".pfx")) {
            return "PKCS12";
        }
        return "JKS";
    }

    /**
     * Opens the file from the classpath ({@code classpath:} prefix) or from the file system.
     *
     * @param path the path
     * @return the input stream
     * @throws IOException if the file cannot be opened
     * @throws IllegalArgumentException if the classpath resource does not exist
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
