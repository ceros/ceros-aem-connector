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

}
