package io.github.programming0026.juniverse.client.util;

import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Sends requests through a proxy except for the {@code nonProxyHosts} destinations, which are reached directly.
 *
 * @author Java Universe
 */
class NonProxyHostsRoutePlanner extends DefaultProxyRoutePlanner {

    /** Lowercase patterns. */
    private final List<String> nonProxyHosts;

    /**
     * Creates the planner.
     *
     * @param proxy the proxy
     * @param nonProxyHosts the patterns of hosts that do not use the proxy
     */
    NonProxyHostsRoutePlanner(HttpHost proxy, String[] nonProxyHosts) {
        super(proxy);
        this.nonProxyHosts = Stream.of(nonProxyHosts)
                .map(pattern -> pattern.trim().toLowerCase(Locale.ROOT))
                .filter(pattern -> !pattern.isEmpty())
                .toList();
    }

    /**
     * Returns the proxy, or {@code null} (direct connection) if the target matches a pattern.
     *
     * @param target the target
     * @param context the request context
     * @return the proxy or {@code null}
     * @throws HttpException if the resolution fails
     */
    @Override
    protected HttpHost determineProxy(HttpHost target, HttpContext context) throws HttpException {
        return matches(target.getHostName(), nonProxyHosts) ? null : super.determineProxy(target, context);
    }

    /**
     * Indicates whether a host matches any pattern.
     *
     * @param host the host
     * @param patterns the patterns, already in lowercase
     * @return true if it matches
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
