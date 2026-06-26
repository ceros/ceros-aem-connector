package com.ceros.util;

public final class FileUtils {

    private FileUtils() {}

    /**
     * Extracts the filename from a URL or path, stripping any query string or fragment.
     *
     * @param urlOrPath a URL or filesystem path
     * @return the filename portion after the last {@code /}, or the input itself if no slash is present
     */
    public static String extractFilename(String urlOrPath) {
        String path = stripQueryParams(urlOrPath);
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Strips the query string ({@code ?...}) and fragment ({@code #...}) from a URL.
     *
     * @param url the URL to clean
     * @return the URL without query or fragment components
     */
    public static String stripQueryParams(String url) {
        int hash = url.indexOf('#');
        if (hash >= 0) {
            url = url.substring(0, hash);
        }
        int qMark = url.indexOf('?');
        if (qMark >= 0) {
            url = url.substring(0, qMark);
        }
        return url;
    }

    /**
     * Validates a server-supplied relative path before it is joined onto a DAM
     * base path. Strips a leading slash, then rejects any {@code .}/{@code ..}
     * traversal or empty segment and any segment containing characters outside
     * the rewrite alphabet ({@code [A-Za-z0-9._-]}). Mirrors the WordPress
     * connector's {@code safe_rel_path} guard.
     *
     * @param path the relative path from the {@code assetRewrites} map
     * @return the validated relative path, or {@code null} when unsafe or blank
     */
    public static String safeRelativePath(String path) {
        if (path == null) {
            return null;
        }
        String trimmed = path;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        for (String segment : trimmed.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                        || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
                if (!ok) {
                    return null;
                }
            }
        }
        return trimmed;
    }

}
