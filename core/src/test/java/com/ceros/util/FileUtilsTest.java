package com.ceros.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUtilsTest {

    @Test
    void extractFilenameFromUrl() {
        assertEquals("style.css", FileUtils.extractFilename("https://cdn.example.com/path/to/style.css"));
        assertEquals("app.js", FileUtils.extractFilename("https://cdn.example.com/app.js?v=2"));
        assertEquals("image.png", FileUtils.extractFilename("image.png"));
        assertEquals("file.txt", FileUtils.extractFilename("/path/file.txt#anchor"));
    }

    @Test
    void stripQueryParamsRemovesQueryAndFragment() {
        assertEquals("https://cdn.example.com/file.css",
                FileUtils.stripQueryParams("https://cdn.example.com/file.css?v=2&t=3"));
        assertEquals("https://cdn.example.com/file.css",
                FileUtils.stripQueryParams("https://cdn.example.com/file.css#section"));
        assertEquals("https://cdn.example.com/file.css",
                FileUtils.stripQueryParams("https://cdn.example.com/file.css#section?v=1"));
        assertEquals("clean", FileUtils.stripQueryParams("clean"));
    }
}
