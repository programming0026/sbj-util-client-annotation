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
import java.util.concurrent.TimeUnit;

/**
 * The Class HttpClientConfigurer.
 */
public class RestClientUtil {
    /** The ssl util. */
    private final SSLUtil sslUtil;

    public RestClientUtil(SSLUtil sslUtil) {
        this.sslUtil = sslUtil;
    }
    /**
     * Configura un RestClient con los parametrise specificities.
     *
     * @param config the config
     * @return the rest client
     */
    public RestClient configureRestClient(RestHttpClient config) {
        try {
            HttpClient httpClient = createHttpClient(config);
            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            factory.setConnectionRequestTimeout((int) config.connectTimeout());
            factory.setReadTimeout((int) config.readTimeout());

            return RestClient.builder()
                    .baseUrl(config.url())
                    .requestFactory(factory)
                    .build();

        } catch (IOException | KeyManagementException | KeyStoreException
                 | NoSuchAlgorithmException | UnrecoverableKeyException
                 | CertificateException e) {
            throw new ClientException("Error configurando RestClient", e);
        }
    }

    /**
     * Configura un RestTemplate con los parámetros especificados.
     *
     * @param config the config
     * @return the rest template
     */
    public RestTemplate configureRestTemplate(RestHttpClient config) {
        try {
            HttpClient httpClient = createHttpClient(config);
            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            factory.setConnectionRequestTimeout((int) config.connectTimeout());
            factory.setReadTimeout((int) config.readTimeout());

            return new RestTemplate(factory);

        } catch (IOException | KeyManagementException | KeyStoreException
                 | NoSuchAlgorithmException | UnrecoverableKeyException
                 | CertificateException e) {
            throw new ClientException("Error configurando RestTemplate", e);
        }
    }




   /**
     * Crea un HttpClient con configuración de timeouts, SSL y pool de conexiones.
     * Connection pool configuration can be customized via annotation parameters:
     * - maxConnections: Total connections in pool (default: 100)
     * - maxConnectionsPerRoute: Connections per route (default: 20)
     * - connectionTimeToLive: TTL for connections in ms (default: -1, no limit)
     * IMPORTANT: Passwords are passed directly from annotation values.
     * Use environment-specific configuration files for sensitive data.
     *
     * @param config the config
     * @return the http client
     * @throws NoSuchAlgorithmException the exception
     */
    private HttpClient createHttpClient(RestHttpClient config)
            throws NoSuchAlgorithmException, UnrecoverableKeyException, CertificateException,
            KeyStoreException, IOException, KeyManagementException {

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(config.connectTimeout(), TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(config.readTimeout(), TimeUnit.MILLISECONDS))
                .setConnectionRequestTimeout(Timeout.of(config.readTimeout(), TimeUnit.MILLISECONDS))
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


        if (config.connectionTimeToLive() > 0) {
            ConnectionConfig connConfig = ConnectionConfig.custom()
                    .setTimeToLive(TimeValue.ofMilliseconds(config.connectionTimeToLive()))
                    .build();
            connectionManagerBuilder.setDefaultConnectionConfig(connConfig);
        }

        PoolingHttpClientConnectionManager connectionManager = connectionManagerBuilder.build();

        return HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .build();
    }
}
