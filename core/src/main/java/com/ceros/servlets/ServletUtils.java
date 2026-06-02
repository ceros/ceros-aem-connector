package com.ceros.servlets;

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
