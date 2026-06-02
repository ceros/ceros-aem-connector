package com.ceros.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared HTTP utilities backed by a single {@link HttpClient} instance.
 *
 * <p>All three Ceros service implementations delegate to this class
 * for outbound HTTP calls, avoiding duplicate {@code HttpClient} thread pools
 * and near-identical request/response boilerplate.</p>
 */
public final class HttpUtils {

    // Pinned explicitly:
    //  - followRedirects(NEVER) so a redirect response cannot be used to bounce
    //    an allowlisted URL into an attacker-controlled or internal target.
    //  - connectTimeout caps the TCP/TLS handshake; the per-request timeout
    //    passed by callers caps the full response time.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private HttpUtils() {}

    /**
     * Validates that {@code url} is safe to use as an outbound-fetch target.
     *
     * <p>Defaults to a strict policy that closes the most common SSRF vectors:</p>
     * <ul>
     *   <li>scheme must be {@code https}</li>
     *   <li>host must not be an IPv4/IPv6 literal (covers cloud metadata
     *       services like {@code 169.254.169.254} and direct RFC1918 reach)</li>
     *   <li>host must not be {@code localhost} or a {@code *.localhost} alias</li>
     * </ul>
     *
     * <p>Both relaxations are intended for dev/test environments only and
     * should remain off in production.</p>
     *
     * @param url                  the URL to validate
     * @param allowHttpScheme      if true, accept {@code http://} in addition to {@code https://}
     * @param allowLocalAddresses  if true, accept IP literals and {@code localhost}-style hosts
     * @throws IllegalArgumentException if the URL is null, unparseable, or violates the policy
     */
    public static void validateOutboundUrl(String url,
                                           boolean allowHttpScheme,
                                           boolean allowLocalAddresses) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL is not parseable: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must include a scheme");
        }
        String lowerScheme = scheme.toLowerCase();
        boolean schemeOk = "https".equals(lowerScheme)
                || (allowHttpScheme && "http".equals(lowerScheme));
        if (!schemeOk) {
            throw new IllegalArgumentException(
                    "URL scheme must be https" + (allowHttpScheme ? " or http" : "")
                            + ": " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must include a host: " + url);
        }
        if (!allowLocalAddresses) {
            if (isIpLiteral(host)) {
                throw new IllegalArgumentException("URL host must not be an IP literal: " + url);
            }
            if (isLocalhostAlias(host)) {
                throw new IllegalArgumentException("URL host must not be localhost: " + url);
            }
        }
    }

    private static boolean isIpLiteral(String host) {
        // IPv6 literal in URI form is bracketed (e.g. "[::1]"); URI.getHost()
        // returns it without brackets but containing a ':', which never
        // appears in DNS names.
        if (host.indexOf(':') >= 0) {
            return true;
        }
        return IPV4_LITERAL.matcher(host).matches();
    }

    private static boolean isLocalhostAlias(String host) {
        String h = host.toLowerCase();
        return "localhost".equals(h) || h.endsWith(".localhost");
    }

    /**
     * Downloads the content at the given URL as a streaming {@link InputStream}.
     * The caller is responsible for closing the returned stream.
     *
     * @param url           the URL to fetch
     * @param timeoutMillis HTTP request timeout in milliseconds
     * @return an open {@code InputStream} of the response body
     * @throws IOException if the request fails or returns a non-200 status
     */
    public static InputStream downloadStream(String url, int timeoutMillis) throws IOException {
        return downloadStream(url, timeoutMillis, Map.of());
    }

    /**
     * Downloads the content at the given URL as a streaming {@link InputStream},
     * with custom request headers.
     *
     * @param url           the URL to fetch
     * @param timeoutMillis HTTP request timeout in milliseconds
     * @param headers       additional request headers (name to value)
     * @return an open {@code InputStream} of the response body
     * @throws IOException if the request fails or returns a non-200 status
     */
    public static InputStream downloadStream(String url, int timeoutMillis,
                                             Map<String, String> headers) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .GET();
        headers.forEach(builder::header);

        try {
            HttpResponse<InputStream> response =
                    HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode() + " for URL: " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + url, e);
        }
    }

    /**
     * Downloads the content at the given URL as a byte array.
     *
     * @param url           the URL to fetch
     * @param timeoutMillis HTTP request timeout in milliseconds
     * @return the full response body as bytes
     * @throws IOException if the request fails or returns a non-200 status
     */
    public static byte[] downloadBytes(String url, int timeoutMillis) throws IOException {
        try (InputStream stream = downloadStream(url, timeoutMillis)) {
            return stream.readAllBytes();
        }
    }

    /**
     * Downloads the content at the given URL as a {@link String}.
     *
     * @param url           the URL to fetch
     * @param timeoutMillis HTTP request timeout in milliseconds
     * @param headers       additional request headers (name to value)
     * @return the response body as a string
     * @throws IOException if the request fails or returns a non-200 status
     */
    public static String fetchString(String url, int timeoutMillis,
                                     Map<String, String> headers) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .GET();
        headers.forEach(builder::header);

        try {
            HttpResponse<String> response =
                    HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for URL: " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted: " + url, e);
        }
    }

    /**
     * Downloads the content at the given URL as a {@link String} with no extra headers.
     *
     * @param url           the URL to fetch
     * @param timeoutMillis HTTP request timeout in milliseconds
     * @return the response body as a string
     * @throws IOException if the request fails or returns a non-200 status
     */
    public static String fetchString(String url, int timeoutMillis) throws IOException {
        return fetchString(url, timeoutMillis, Map.of());
    }
}
