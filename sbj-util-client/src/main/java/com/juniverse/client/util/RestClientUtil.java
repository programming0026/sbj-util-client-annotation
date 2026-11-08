package com.juniverse.client.util;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.SSLConfig;
import com.juniverse.client.exception.ClientException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Construye el {@link RestClient} o {@link RestTemplate} de cada cliente sobre Apache HttpClient 5,
 * con timeouts, SSL y pool de conexiones tomados de su anotación {@link RestHttpClient}.
 */
public class RestClientUtil {
    /** Utilidad para crear los contextos SSL. */
    private final SSLUtil sslUtil;

    /**
     * Crea la utilidad.
     *
     * @param sslUtil la utilidad SSL
     */
    public RestClientUtil(SSLUtil sslUtil) {
        this.sslUtil = sslUtil;
    }
    /**
     * Crea la request factory de Apache HttpClient 5 con su pool de conexiones.
     *
     * Quien la crea es responsable de cerrarla con {@link HttpComponentsClientHttpRequestFactory#destroy()}
     * cuando ya no se use, para liberar el pool.
     *
     * @param config la configuración del cliente
     * @return la request factory
     * @throws ClientException si falla la configuración SSL
     */
    public HttpComponentsClientHttpRequestFactory createRequestFactory(RestHttpClient config) {
        try {
            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(createHttpClient(config));

            factory.setConnectionRequestTimeout(Duration.ofMillis(config.connectTimeout()));
            factory.setReadTimeout(Duration.ofMillis(config.readTimeout()));
            return factory;

        } catch (IOException | KeyManagementException | KeyStoreException
                 | NoSuchAlgorithmException | UnrecoverableKeyException
                 | CertificateException e) {
            throw new ClientException("Error configuring HTTP client", e);
        }
    }

    /**
     * Configura un RestClient sobre la request factory indicada.
     *
     * @param config la configuración del cliente
     * @param requestFactory la request factory
     * @return el RestClient
     */
    public RestClient configureRestClient(RestHttpClient config, ClientHttpRequestFactory requestFactory) {
        return configureRestClient(config, requestFactory, RestClient.builder());
    }

    /**
     * Configura un RestClient a partir de un builder ya personalizado, fijando la request factory y la URL
     * base del cliente (reemplazan lo que el builder tuviera).
     *
     * @param config la configuración del cliente
     * @param requestFactory la request factory
     * @param builder el builder a completar
     * @return el RestClient
     */
    public RestClient configureRestClient(RestHttpClient config, ClientHttpRequestFactory requestFactory,
                                          RestClient.Builder builder) {
        return builder
                .baseUrl(config.url())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Configura un RestTemplate sobre la request factory indicada.
     *
     * @param requestFactory la request factory
     * @return el RestTemplate
     */
    public RestTemplate configureRestTemplate(ClientHttpRequestFactory requestFactory) {
        return new RestTemplate(requestFactory);
    }

    /**
     * Configura un RestClient con su propia request factory (que no se cierra automáticamente).
     *
     * @param config la configuración del cliente
     * @return el RestClient
     * @throws ClientException si falla la configuración SSL
     */
    public RestClient configureRestClient(RestHttpClient config) {
        return configureRestClient(config, createRequestFactory(config));
    }

    /**
     * Configura un RestTemplate con su propia request factory (que no se cierra automáticamente).
     *
     * @param config la configuración del cliente
     * @return el RestTemplate
     * @throws ClientException si falla la configuración SSL
     */
    public RestTemplate configureRestTemplate(RestHttpClient config) {
        return configureRestTemplate(createRequestFactory(config));
    }




   /**
     * Crea un HttpClient con configuración de timeouts, SSL y pool de conexiones.
     *
     * El pool se configura con los atributos de la anotación:
     * - maxConnections: conexiones totales del pool (por defecto 100)
     * - maxConnectionsPerRoute: conexiones por ruta (por defecto 20)
     * - connectionTimeToLive: tiempo de vida de las conexiones en ms (por defecto -1, sin límite)
     *
     * Si SSL está habilitado sin keystore ni truststore se usa el contexto SSL por defecto de la JVM.
     * IMPORTANTE: las contraseñas se toman tal cual de la anotación.
     *
     * @param config la configuración del cliente
     * @return el HttpClient
     * @throws NoSuchAlgorithmException si el algoritmo TLS no está disponible
     * @throws UnrecoverableKeyException si no se puede recuperar la clave del keystore
     * @throws CertificateException si un certificado no se puede cargar
     * @throws KeyStoreException si el keystore no es válido
     * @throws IOException si el keystore o truststore no se puede leer
     * @throws KeyManagementException si falla la inicialización del contexto SSL
     */
    private HttpClient createHttpClient(RestHttpClient config)
            throws NoSuchAlgorithmException, UnrecoverableKeyException, CertificateException,
            KeyStoreException, IOException, KeyManagementException {

        // connectionRequestTimeout: espera máxima para obtener una conexión del pool.
        // responseTimeout: espera máxima de la respuesta.
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(config.connectTimeout(), TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(config.readTimeout(), TimeUnit.MILLISECONDS))
                .build();


        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(config.readTimeout(), TimeUnit.MILLISECONDS))
                .build();


        SSLContext sslContext;
        SSLConfig sslConfig = config.ssl();

        if (sslConfig.enabled()) {
            if (sslConfig.keystorePath().isEmpty() && sslConfig.truststorePath().isEmpty()) {

                sslContext = SSLContext.getDefault();
            } else {

                String keystorePassword = sslConfig.keystorePassword();
                String truststorePassword = sslConfig.truststorePassword();

                sslContext = this.sslUtil.createSSLContext(
                        sslConfig.keystorePath(),
                        keystorePassword,
                        sslConfig.truststorePath(),
                        truststorePassword
                );
            }
        } else {

            sslContext = sslUtil.createInsecureSSLContext();
        }


        ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create()
                .setSslContext(sslContext);

        if (!sslConfig.enabled()) {

            tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }


        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setTlsSocketStrategy(tlsStrategyBuilder.buildClassic())
                        .setDefaultSocketConfig(socketConfig)
                        .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                        .setConnPoolPolicy(PoolReusePolicy.LIFO)
                        .setMaxConnTotal(config.maxConnections())
                        .setMaxConnPerRoute(config.maxConnectionsPerRoute());


        // En HttpClient 5 el timeout de conexión TCP se define aquí; sin él se usa el valor por
        // defecto de HttpClient (3 minutos).
        ConnectionConfig.Builder connConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(config.connectTimeout(), TimeUnit.MILLISECONDS));
        if (config.connectionTimeToLive() > 0) {
            connConfig.setTimeToLive(TimeValue.ofMilliseconds(config.connectionTimeToLive()));
        }
        connectionManagerBuilder.setDefaultConnectionConfig(connConfig.build());

        PoolingHttpClientConnectionManager connectionManager = connectionManagerBuilder.build();

        // Los reintentos los gestiona Resilience4j (RetryConfig y ErrorHandler.shouldRetry). Con la estrategia
        // por defecto de HttpClient, cada GET/PUT/DELETE se reintentaba además por su cuenta ante 503/429 y
        // errores de E/S, esperando 1 s: se duplicaban las peticiones y se ignoraba maxRetries.
        return HttpClientBuilder.create()
                .disableAutomaticRetries()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .build();
    }
}
