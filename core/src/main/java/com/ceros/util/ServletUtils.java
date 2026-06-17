package com.ceros.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletResponse;

import java.io.IOException;
import java.util.Map;

public final class ServletUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ServletUtils() {}

    public static void writeError(SlingHttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setStatus(statusCode);
        OBJECT_MAPPER.writeValue(response.getWriter(),
                Map.of("status", "error", "message", message));
    }

    public static void writeError(SlingHttpServletResponse response, ServletErrorException e)
            throws IOException {
        writeError(response, e.getStatusCode(), e.getMessage());
    }

    public static void writeJson(SlingHttpServletResponse response, Object data)
            throws IOException {
        OBJECT_MAPPER.writeValue(response.getWriter(), data);
    }

    /**
     * Normalises and validates a {@code componentPath} request parameter for the
     * authoring endpoints (fetch / import). Decodes Sling's {@code _jcr_content}
     * URL form and rejects anything that isn't a safe content path.
     *
     * @return the normalised path, or {@code null} if absent or invalid
     */
    public static String normaliseComponentPath(String raw) {
        if (raw == null) {
            return null;
        }
        String componentPath = raw.trim();
        if (componentPath.isEmpty()) {
            return null;
        }
        // Sling encodes jcr:content as _jcr_content in URLs since colons aren't URL-safe.
        componentPath = componentPath.replace("_jcr_content", "jcr:content");
        if (!componentPath.startsWith("/content/")
                || componentPath.contains("..")
                || componentPath.contains("*")) {
            return null;
        }
        return componentPath;
    }

    public static class ServletErrorException extends Exception {
        private final int statusCode;

        public ServletErrorException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
