package io.github.programming0026.juniverse.client.util;

import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Envía por un proxy salvo los destinos de {@code nonProxyHosts}, a los que se llega directamente.
 *
 * @author Java Universe
 */
class NonProxyHostsRoutePlanner extends DefaultProxyRoutePlanner {

    /** Patrones en minúsculas. */
    private final List<String> nonProxyHosts;

    /**
     * Crea el planificador.
     *
     * @param proxy el proxy
     * @param nonProxyHosts los patrones de hosts que no usan el proxy
     */
    NonProxyHostsRoutePlanner(HttpHost proxy, String[] nonProxyHosts) {
        super(proxy);
        this.nonProxyHosts = Stream.of(nonProxyHosts)
                .map(pattern -> pattern.trim().toLowerCase(Locale.ROOT))
                .filter(pattern -> !pattern.isEmpty())
                .toList();
    }

    /**
     * Devuelve el proxy, o {@code null} (conexión directa) si el destino coincide con un patrón.
     *
     * @param target el destino
     * @param context el contexto de la petición
     * @return el proxy o {@code null}
     * @throws HttpException si falla la resolución
     */
    @Override
    protected HttpHost determineProxy(HttpHost target, HttpContext context) throws HttpException {
        return matches(target.getHostName(), nonProxyHosts) ? null : super.determineProxy(target, context);
    }

    /**
     * Indica si un host coincide con algún patrón.
     *
     * @param host el host
     * @param patterns los patrones, ya en minúsculas
     * @return true si coincide
     */
    static boolean matches(String host, List<String> patterns) {
        String name = host.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            boolean match;
            if (pattern.length() > 1 && pattern.startsWith("*")) {
                match = name.endsWith(pattern.substring(1));
            } else if (pattern.length() > 1 && pattern.endsWith("*")) {
                match = name.startsWith(pattern.substring(0, pattern.length() - 1));
            } else {
                match = name.equals(pattern);
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
