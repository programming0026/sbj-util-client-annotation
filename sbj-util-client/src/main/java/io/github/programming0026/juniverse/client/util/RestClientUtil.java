package io.github.programming0026.juniverse.client.util;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.ProxyConfig;
import io.github.programming0026.juniverse.client.autoconfigure.SSLConfig;
import io.github.programming0026.juniverse.client.exception.ClientException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Builds the {@link RestClient} or {@link RestTemplate} of each client on top of Apache HttpClient 5,
 * with timeouts, SSL and connection pool taken from its {@link RestHttpClient} annotation.
 *
 * @author Java Universe
 */
public class RestClientUtil {
    /** Utility to create the SSL contexts. */
    private final SSLUtil sslUtil;

    /**
     * Creates the utility.
     *
     * @param sslUtil the SSL utility
     */
    public RestClientUtil(SSLUtil sslUtil) {
        this.sslUtil = sslUtil;
    }
    /**
     * Creates the Apache HttpClient 5 request factory with its connection pool.
     * Whoever creates it is responsible for closing it with {@link HttpComponentsClientHttpRequestFactory#destroy()}
     * when it is no longer used, to release the pool.
     *
     * @param config the client configuration
     * @return the request factory
     * @throws ClientException if the SSL configuration fails
     */
    public HttpComponentsClientHttpRequestFactory createRequestFactory(RestHttpClient config) {
        try {
            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(createHttpClient(config));

            factory.setConnectionRequestTimeout(Duration.ofMillis(config.connectTimeout()));
            factory.setReadTimeout(Duration.ofMillis(config.readTimeout()));
            factory.setHttpContextFactory((method, uri) -> methodContext(config));
            return factory;

        } catch (IOException | KeyManagementException | KeyStoreException
                 | NoSuchAlgorithmException | UnrecoverableKeyException
                 | CertificateException e) {
            throw new ClientException("Error configuring HTTP client", e);
        }
    }

    /**
     * Creates the request context when the method has its own {@code readTimeout}
     * ({@link MethodReadTimeout}); otherwise it returns null and the client's configuration is used. Being a
     * {@code RequestConfig} of its own, Spring respects it as is, so it repeats the client's pool connection
     * wait timeout and the redirect settings.
     *
     * @param config the client configuration
     * @return the context with the method's timeout, or null
     */
    private static HttpClientContext methodContext(RestHttpClient config) {
        return MethodReadTimeout.current().stream().mapToObj(millis -> {
            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.of(config.connectTimeout(), TimeUnit.MILLISECONDS))
                    .setResponseTimeout(Timeout.of(millis, TimeUnit.MILLISECONDS))
                    .setRedirectsEnabled(config.followRedirects())
                    .setMaxRedirects(config.maxRedirects())
                    .build());
            return context;
        }).findFirst().orElse(null);
    }

    /**
     * Creates the builder of a RestClient with the client's request factory and base URL, ready for further
     * customizers to be applied to it.
     *
     * @param config the client configuration
     * @param requestFactory the request factory
     * @return the builder
     */
    public RestClient.Builder restClientBuilder(RestHttpClient config, ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .baseUrl(config.url())
                .requestFactory(requestFactory);
    }

    /**
     * Configures a RestClient on the given request factory.
     *
     * @param config the client configuration
     * @param requestFactory the request factory
     * @return the RestClient
     */
    public RestClient configureRestClient(RestHttpClient config, ClientHttpRequestFactory requestFactory) {
        return configureRestClient(config, requestFactory, RestClient.builder());
    }

    /**
     * Configures a RestClient from an already customized builder, setting the client's request factory and
     * base URL (they replace whatever the builder had).
     *
     * @param config the client configuration
     * @param requestFactory the request factory
     * @param builder the builder to complete
     * @return the RestClient
     */
    public RestClient configureRestClient(RestHttpClient config, ClientHttpRequestFactory requestFactory,
                                          RestClient.Builder builder) {
        return builder
                .baseUrl(config.url())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Configures a RestTemplate on the given request factory.
     *
     * @param requestFactory the request factory
     * @return the RestTemplate
     */
    public RestTemplate configureRestTemplate(ClientHttpRequestFactory requestFactory) {
        return new RestTemplate(requestFactory);
    }

    /**
     * Configures a RestClient with its own request factory (which is not closed automatically).
     *
     * @param config the client configuration
     * @return the RestClient
     * @throws ClientException if the SSL configuration fails
     */
    public RestClient configureRestClient(RestHttpClient config) {
        return configureRestClient(config, createRequestFactory(config));
    }

    /**
     * Configures a RestTemplate with its own request factory (which is not closed automatically).
     *
     * @param config the client configuration
     * @return the RestTemplate
     * @throws ClientException if the SSL configuration fails
     */
    public RestTemplate configureRestTemplate(RestHttpClient config) {
        return configureRestTemplate(createRequestFactory(config));
    }




   /**
     * Creates an HttpClient with timeout, SSL and connection pool configuration.
     * The pool is configured with the annotation's attributes:
     * - maxConnections: total connections of the pool (default 100)
     * - maxConnectionsPerRoute: connections per route (default 20)
     * - connectionTimeToLive: time to live of the connections in ms (default -1, no limit)
     * If SSL is enabled without keystore or truststore, the JVM's default SSL context is used.
     * IMPORTANT: passwords are taken as is from the annotation.
     *
     * @param config the client configuration
     * @return the HttpClient
     * @throws NoSuchAlgorithmException if the TLS algorithm is not available
     * @throws UnrecoverableKeyException if the key cannot be recovered from the keystore
     * @throws CertificateException if a certificate cannot be loaded
     * @throws KeyStoreException if the keystore is not valid
     * @throws IOException if the keystore or truststore cannot be read
     * @throws KeyManagementException if the SSL context initialization fails
     */
    private HttpClient createHttpClient(RestHttpClient config)
            throws NoSuchAlgorithmException, UnrecoverableKeyException, CertificateException,
            KeyStoreException, IOException, KeyManagementException {

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(config.connectTimeout(), TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(config.readTimeout(), TimeUnit.MILLISECONDS))
                .setRedirectsEnabled(config.followRedirects())
                .setMaxRedirects(config.maxRedirects())
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


        ConnectionConfig.Builder connConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(config.connectTimeout(), TimeUnit.MILLISECONDS));
        if (config.connectionTimeToLive() > 0) {
            connConfig.setTimeToLive(TimeValue.ofMilliseconds(config.connectionTimeToLive()));
        }
        connectionManagerBuilder.setDefaultConnectionConfig(connConfig.build());

        PoolingHttpClientConnectionManager connectionManager = connectionManagerBuilder.build();

        HttpClientBuilder builder = HttpClientBuilder.create()
                .disableAutomaticRetries()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager);
        if (!config.followRedirects()) {
            builder.disableRedirectHandling();
        }
        if (config.maxRedirects() < 1) {
            throw new ClientException("maxRedirects must be at least 1, but is " + config.maxRedirects());
        }
        configureProxy(builder, config.proxy());
        return builder.build();
    }

    /**
     * Applies the proxy to the HttpClient if it has a host: the route through the proxy (except
     * {@code nonProxyHosts}) and, if there is a user, the credentials for the proxy.
     *
     * @param builder the HttpClient builder
     * @param proxy the proxy configuration
     * @throws ClientException if the port, the scheme or the credentials are not valid
     */
    private static void configureProxy(HttpClientBuilder builder, ProxyConfig proxy) {
        if (proxy.host().isBlank()) {
            return;
        }
        int port = parsePort(proxy.port());
        if (!"http".equalsIgnoreCase(proxy.scheme()) && !"https".equalsIgnoreCase(proxy.scheme())) {
            throw new ClientException("The proxy scheme must be http or https, but is '" + proxy.scheme() + "'");
        }
        if (proxy.username().isEmpty() && !proxy.password().isEmpty()) {
            throw new ClientException("The proxy declares a password but no username");
        }
        HttpHost proxyHost = new HttpHost(proxy.scheme().toLowerCase(Locale.ROOT), proxy.host().trim(), port);
        builder.setRoutePlanner(new NonProxyHostsRoutePlanner(proxyHost, proxy.nonProxyHosts()));
        if (!proxy.username().isEmpty()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(new AuthScope(proxyHost),
                    new UsernamePasswordCredentials(proxy.username(), proxy.password().toCharArray()));
            builder.setDefaultCredentialsProvider(credentials);
        }
    }

    /**
     * Converts the proxy port.
     *
     * @param text the port as text
     * @return the port
     * @throws ClientException if it is not a number between 1 and 65535
     */
    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // reported below with the same message
        }
        throw new ClientException("The proxy port must be a number between 1 and 65535, but is '" + text + "'");
    }
}
