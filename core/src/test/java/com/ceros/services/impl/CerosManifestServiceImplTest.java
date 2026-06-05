package com.ceros.services.impl;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CerosManifestServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CerosManifestServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CerosManifestServiceImpl();
        setField(service, "httpTimeoutMillis", 10000);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = CerosManifestServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static CerosManifestV0 parse(String json) throws IOException {
        return MAPPER.readValue(json, CerosManifestV0.class);
    }

    @Test
    void fetchPublicManifestFromUrlThrowsOnInvalidUrl() {
        assertThrows(Exception.class, () -> service.fetchPublicManifestFromUrl("not-a-url"));
    }

    @Test
    void fetchPublicManifestFromUrlTrimsUrl() {
        assertThrows(IOException.class,
                () -> service.fetchPublicManifestFromUrl("  https://nonexistent.invalid/manifest.json  "));
    }

    @Test
    void fetchManifestBundleIncludesPrimaryUnderPageSlug() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);

        CerosManifestV0 primary = parse(""
                + "{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":[{\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true}]"
                + "}");
        doReturn(primary).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        assertEquals("home", bundle.getPrimarySlug());
        assertEquals(1, bundle.getPagesBySlug().size());
        assertSame(primary, bundle.manifestFor("home"));
    }

    @Test
    void fetchManifestBundleFetchesNonCurrentPages() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);

        CerosManifestV0 primary = parse(""
                + "{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"}"
                + "  ]"
                + "}");
        CerosManifestV0 about = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"about\"}}");
        doReturn(primary).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");
        doReturn(about).when(spy).fetchPublicManifestFromUrl("https://x/about/manifest.json");

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        assertEquals(2, bundle.getPagesBySlug().size());
        assertSame(about, bundle.manifestFor("about"));
    }

    @Test
    void fetchManifestBundleSkipsPagesMissingManifestUrl() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);

        CerosManifestV0 primary = parse(""
                + "{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"current\":true},"
                + "    {\"slug\":\"about\"}"
                + "  ]"
                + "}");
        doReturn(primary).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        assertEquals(1, bundle.getPagesBySlug().size());
        verify(spy, times(1)).fetchPublicManifestFromUrl(anyString());
    }

    @Test
    void fetchManifestBundleSwallowsPerPageFetchErrors() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);

        CerosManifestV0 primary = parse(""
                + "{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"},"
                + "    {\"slug\":\"contact\",\"manifestUrl\":\"https://x/contact/manifest.json\"}"
                + "  ]"
                + "}");
        CerosManifestV0 contact = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"contact\"}}");
        doReturn(primary).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");
        doThrow(new IOException("boom")).when(spy).fetchPublicManifestFromUrl("https://x/about/manifest.json");
        doReturn(contact).when(spy).fetchPublicManifestFromUrl("https://x/contact/manifest.json");

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        // Primary + contact succeeded, about was skipped.
        assertEquals(2, bundle.getPagesBySlug().size());
        assertNotNull(bundle.manifestFor("home"));
        assertNotNull(bundle.manifestFor("contact"));
        assertNull(bundle.getPagesBySlug().get("about"));
    }

    @Test
    void fetchManifestBundlePropagatesPrimaryFetchFailure() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);
        doThrow(new IOException("nope")).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        assertThrows(IOException.class, () -> spy.fetchManifestBundle("https://x/home/manifest.json"));
    }
}
