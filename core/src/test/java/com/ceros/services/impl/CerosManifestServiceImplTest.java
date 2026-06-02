package com.ceros.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class CerosManifestServiceImplTest {

    private CerosManifestServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CerosManifestServiceImpl();
        setField("httpTimeoutMillis", 10000);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosManifestServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    void fetchPublicManifestFromUrlThrowsOnInvalidUrl() {
        assertThrows(Exception.class, () -> service.fetchPublicManifestFromUrl("not-a-url"));
    }

    @Test
    void fetchPublicManifestFromUrlTrimsUrl() throws Exception {
        assertThrows(IOException.class, () -> service.fetchPublicManifestFromUrl("  https://nonexistent.invalid/manifest.json  "));
    }
}
