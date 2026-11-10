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
 * Crea contextos SSL a partir de un keystore y/o truststore, o un contexto inseguro solo para desarrollo.
 *
 * @author Java Universe
 */
public class SSLUtil {
    /** El logger. */
    private static final Logger LOG = LoggerFactory.getLogger(SSLUtil.class);

    /** Proveedor del entorno del que se leen los perfiles activos. */
    private final Supplier<Environment> environment;

    /**
     * Crea la utilidad sin contexto de Spring: los perfiles se leen en cada llamada de
     * {@code spring.profiles.active} (propiedad del sistema) o {@code SPRING_PROFILES_ACTIVE} (variable de
     * entorno).
     */
    public SSLUtil() {
        this.environment = StandardEnvironment::new;
    }

    /**
     * Crea la utilidad usando los perfiles activos del {@link Environment} de Spring (incluye los definidos
     * en {@code application.properties}).
     *
     * @param environment el entorno de Spring
     */
    public SSLUtil(Environment environment) {
        this.environment = () -> environment;
    }

    /**
     * Crea el contexto SSL. Las rutas admiten el prefijo {@code classpath:}; el tipo de almacén se deduce
     * de la extensión ({@code .p12}/{@code .pfx} es PKCS12, cualquier otra es JKS). Una ruta vacía o
     * {@code null} usa los valores por defecto de la JVM para esa parte.
     *
     * @param keystorePath la ruta del keystore
     * @param keystorePassword la contraseña del keystore
     * @param truststorePath la ruta del truststore
     * @param truststorePassword la contraseña del truststore
     * @return el contexto SSL
     * @throws NoSuchAlgorithmException si el algoritmo TLS no está disponible
     * @throws java.security.UnrecoverableKeyException si no se puede recuperar la clave del keystore
     * @throws java.security.cert.CertificateException si un certificado no se puede cargar
     * @throws java.security.KeyStoreException si el almacén no es válido
     * @throws java.io.IOException si el archivo no se puede leer
     * @throws java.security.KeyManagementException sí falla la inicialización del contexto
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
     * Crea un contexto SSL inseguro.
     * ADVERTENCIA: desactiva toda validación de certificados SSL/TLS y NUNCA debe usarse en producción;
     * deja la aplicación expuesta a ataques Man-in-the-Middle (MITM).
     * Solo se permite en entornos de desarrollo: alguno de los perfiles activos debe ser dev, local o
     * development (sin distinguir mayúsculas; se admiten varios perfiles, por ejemplo {@code dev,kafka}).
     *
     * @return el contexto SSL
     * @throws NoSuchAlgorithmException si el algoritmo TLS no está disponible
     * @throws java.security.KeyManagementException sí falla la inicialización del contexto
     * @throws IllegalStateException si no es un entorno de desarrollo
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
     * Indica si alguno de los perfiles activos es de desarrollo.
     *
     * @param activeProfiles los perfiles activos
     * @return true si alguno es dev, local o development
     */
    private static boolean isDevelopmentEnvironment(String[] activeProfiles) {
        return Arrays.stream(activeProfiles)
                .map(String::trim)
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile)
                        || "local".equalsIgnoreCase(profile)
                        || "development".equalsIgnoreCase(profile));
    }

    /**
     * Crea los key managers a partir del keystore.
     *
     * @param keystorePath la ruta del keystore
     * @param password la contraseña
     * @return los key managers
     * @throws KeyStoreException si el keystore no es válido
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
     * Crea los trust managers a partir del truststore.
     *
     * @param truststorePath la ruta del truststore
     * @param password la contraseña
     * @return los trust managers
     * @throws KeyStoreException si el truststore no es válido
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
     * Deduce el tipo de almacén por la extensión del archivo.
     *
     * @param path la ruta
     * @return PKCS12 para '.p12/.pfx' JKS en cualquier otro caso
     */
    private static String getKeystoreType(String path) {
        if (path.endsWith(".p12") || path.endsWith(".pfx")) {
            return "PKCS12";
        }
        return "JKS";
    }

    /**
     * Abre el archivo desde el classpath (prefijo {@code classpath:}) o desde el sistema de archivos.
     *
     * @param path la ruta
     * @return el flujo de lectura
     * @throws IOException si el archivo no se puede abrir
     * @throws IllegalArgumentException si el recurso del classpath no existe
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
