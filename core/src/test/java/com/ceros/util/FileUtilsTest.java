package com.ceros.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void safeRelativePathAcceptsCleanPathsAndStripsLeadingSlash() {
        assertEquals("a.css", FileUtils.safeRelativePath("a.css"));
        assertEquals("assets/styles/reset.css",
                FileUtils.safeRelativePath("assets/styles/reset.css"));
        assertEquals("assets/styles/reset.css",
                FileUtils.safeRelativePath("/assets/styles/reset.css"));
        assertEquals("a1b2c3-font.woff2", FileUtils.safeRelativePath("a1b2c3-font.woff2"));
    }

    @Test
    void safeRelativePathRejectsTraversalAndUnsafeChars() {
        assertNull(FileUtils.safeRelativePath(null));
        assertNull(FileUtils.safeRelativePath(""));
        assertNull(FileUtils.safeRelativePath("/"));
        assertNull(FileUtils.safeRelativePath("../escape.css"));
        assertNull(FileUtils.safeRelativePath("a/../b.css"));
        assertNull(FileUtils.safeRelativePath("a/./b.css"));
        assertNull(FileUtils.safeRelativePath("a//b.css"));
        assertNull(FileUtils.safeRelativePath("sub/has space.css"));
        assertNull(FileUtils.safeRelativePath("weird?.css"));
        assertNull(FileUtils.safeRelativePath("emoji😀.css"));
    }
}
