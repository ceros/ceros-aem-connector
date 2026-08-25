package com.ceros.services.impl;

import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.FetchProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CerosManifestServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> CEROS_DOMAINS =
            Arrays.asList("ceros.site");

    private CerosManifestServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CerosManifestServiceImpl();
        setField(service, "httpTimeoutMillis", 10000);
        setField(service, "cerosOwnedDomains", CEROS_DOMAINS);
        setField(service, "allowUntrustedManifestHost", false);
    }

    /** Production-posture spy with the Ceros whitelist enforced. */
    private CerosManifestServiceImpl prodSpy() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);
        setField(spy, "cerosOwnedDomains", CEROS_DOMAINS);
        setField(spy, "allowUntrustedManifestHost", false);
        return spy;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = CerosManifestServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static CerosManifestV1 parse(String json) throws IOException {
        return MAPPER.readValue(json, CerosManifestV1.class);
    }

    /**
     * Wires a resolver whose session exposes a single component node, so the
     * bundle-persist path can be exercised against a mock repository.
     */
    private static Node mockComponentNode(ResourceResolver resolver, String path) throws Exception {
        Session session = mock(Session.class);
        Node node = mock(Node.class);
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(session.nodeExists(path)).thenReturn(true);
        when(session.getNode(path)).thenReturn(node);
        return node;
    }

    private static StoredManifestBundle bundleWith(String manifestJson) throws IOException {
        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        pages.put("page-1", parse(manifestJson));
        return new StoredManifestBundle("page-1", pages);
    }

    @Test
    void storingBundleWritesExperienceResourceIdFromPrimaryManifest() throws Exception {
        ResourceResolver resolver = mock(ResourceResolver.class);
        Node node = mockComponentNode(resolver, "/content/x");

        boolean saved = service.storeManifestBundle(resolver, "/content/x",
                "https://acme.ceros.site/exp/manifest.v1.json",
                bundleWith("{\"experience\":{\"experienceResourceId\":\"exp-abc-123\",\"slug\":\"exp\"}}"),
                Map.of());

        assertTrue(saved);
        verify(node).setProperty("cerosExperienceResourceId", "exp-abc-123");
    }

    @Test
    void storingBundleWithoutResourceIdClearsStaleProperty() throws Exception {
        // An experience exported or published before the manifest carried the field.
        ResourceResolver resolver = mock(ResourceResolver.class);
        Node node = mockComponentNode(resolver, "/content/x");
        when(node.hasProperty("cerosExperienceResourceId")).thenReturn(true);
        Property stale = mock(Property.class);
        when(node.getProperty("cerosExperienceResourceId")).thenReturn(stale);

        service.storeManifestBundle(resolver, "/content/x", null,
                bundleWith("{\"experience\":{\"slug\":\"exp\"}}"), Map.of());

        verify(stale).remove();
        verify(node, never()).setProperty(eq("cerosExperienceResourceId"), anyString());
    }

    @Test
    void storingBundleWithNoResourceIdAndNoStaleValueWritesNothing() throws Exception {
        ResourceResolver resolver = mock(ResourceResolver.class);
        Node node = mockComponentNode(resolver, "/content/x");
        when(node.hasProperty("cerosExperienceResourceId")).thenReturn(false);

        service.storeManifestBundle(resolver, "/content/x", null,
                bundleWith("{\"experience\":{\"slug\":\"exp\"}}"), Map.of());

        verify(node, never()).setProperty(eq("cerosExperienceResourceId"), anyString());
        verify(node, never()).getProperty("cerosExperienceResourceId");
    }

    @Test
    void fetchPublicManifestFromUrlThrowsOnInvalidUrl() {
        assertThrows(Exception.class, () -> service.fetchPublicManifestFromUrl("not-a-url"));
    }

    @Test
    void fetchPublicManifestFromUrlTrimsUrl() {
        // Ceros-owned host so it passes the whitelist gate; the DNS miss then
        // surfaces as an IOException (proving the URL was trimmed and fetched).
        assertThrows(IOException.class,
                () -> service.fetchPublicManifestFromUrl("  https://nonexistent.ceros.site/manifest.json  "));
    }

    @Test
    void fetchPublicManifestFromUrlRejectsNonCerosHost() {
        // The whitelist gate fires before any network call.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.fetchPublicManifestFromUrl("https://customer.com/exp/manifest.v1.json"));
        assertTrue(e.getMessage().contains("Ceros"));
    }

    // ---- resolveTrustedManifestUrl ----

    @Test
    void resolveTrustedManifestUrlAppendsManifestFilenameForCerosHost() throws Exception {
        assertEquals("https://acme.ceros.site/exp/manifest.v1.json",
                service.resolveTrustedManifestUrl("https://acme.ceros.site/exp"));
    }

    @Test
    void resolveTrustedManifestUrlKeepsExistingManifestUrlForCerosHost() throws Exception {
        assertEquals("https://acme.ceros.site/exp/manifest.v1.json",
                service.resolveTrustedManifestUrl("https://acme.ceros.site/exp/manifest.v1.json"));
    }

    @Test
    void resolveTrustedManifestUrlUsesFlexManifestHeaderForVanityHost() throws Exception {
        CerosManifestServiceImpl spy = prodSpy();
        // Vanity page advertises its canonical Ceros-owned manifest URL.
        doReturn(Optional.of("https://acme.ceros.site/exp/manifest.v1.json"))
                .when(spy).fetchFlexManifestHeader("https://look.customer.com/exp");

        assertEquals("https://acme.ceros.site/exp/manifest.v1.json",
                spy.resolveTrustedManifestUrl("https://look.customer.com/exp"));
    }

    @Test
    void resolveTrustedManifestUrlRejectsVanityHostWithNoHeader() throws Exception {
        CerosManifestServiceImpl spy = prodSpy();
        doReturn(Optional.empty()).when(spy).fetchFlexManifestHeader("https://look.customer.com/exp");

        assertThrows(IllegalArgumentException.class,
                () -> spy.resolveTrustedManifestUrl("https://look.customer.com/exp"));
    }

    @Test
    void resolveTrustedManifestUrlRejectsSpoofedHeaderPointingOffCeros() throws Exception {
        CerosManifestServiceImpl spy = prodSpy();
        // A malicious page advertises a non-Ceros manifest — must be rejected.
        doReturn(Optional.of("https://evil.com/exp/manifest.v1.json"))
                .when(spy).fetchFlexManifestHeader("https://look.customer.com/exp");

        assertThrows(IllegalArgumentException.class,
                () -> spy.resolveTrustedManifestUrl("https://look.customer.com/exp"));
    }

    @Test
    void resolveTrustedManifestUrlRejectsNonHttps() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveTrustedManifestUrl("http://acme.ceros.site/exp"));
    }

    @Test
    void resolveTrustedManifestUrlTrustsAnyHostWhenRelaxationOn() throws Exception {
        // Dev/test posture: localhost manifests are accepted without the header.
        CerosManifestServiceImpl dev = new CerosManifestServiceImpl();
        setField(dev, "httpTimeoutMillis", 10000);
        setField(dev, "cerosOwnedDomains", CEROS_DOMAINS);
        setField(dev, "allowUntrustedManifestHost", true);
        setField(dev, "allowHttpScheme", true);
        setField(dev, "allowLocalAddresses", true);

        assertEquals("http://ceros-qa.localhost:8900/exp/manifest.v1.json",
                dev.resolveTrustedManifestUrl("http://ceros-qa.localhost:8900/exp"));
    }

    @Test
    void fetchManifestBundleIncludesPrimaryUnderPageSlug() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);

        CerosManifestV1 primary = parse(""
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

        CerosManifestV1 primary = parse(""
                + "{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"}"
                + "  ]"
                + "}");
        CerosManifestV1 about = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"about\"}}");
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

        CerosManifestV1 primary = parse(""
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

        CerosManifestV1 primary = parse(""
                + "{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"},"
                + "    {\"slug\":\"contact\",\"manifestUrl\":\"https://x/contact/manifest.json\"}"
                + "  ]"
                + "}");
        CerosManifestV1 contact = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"contact\"}}");
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

    // ---- performFetchAndStore ----

    @Test
    void performFetchAndStoreDrivesProgressThroughEveryPhase() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);

        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        setField(spy, "cerosAssetStorageService", assets);
        when(assets.uploadAssets(any(), any())).thenReturn(Map.of("https://cdn/a.css", "/dam/a.css"));

        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        pages.put("home", parse("{}"));
        pages.put("about", parse("{}"));
        StoredManifestBundle bundle = new StoredManifestBundle("home", pages);
        doReturn(bundle).when(spy).fetchManifestBundle("https://x/manifest.json");
        doReturn(true).when(spy).storeManifestBundle(any(), eq("/content/x"), anyString(), any(), any());

        RecordingProgress progress = new RecordingProgress();
        ResourceResolver resolver = mock(ResourceResolver.class);

        spy.performFetchAndStore("https://x/manifest.json", "/content/x", progress, resolver);

        // Phases visited in order, with the upload phase always preceding asset uploads.
        assertEquals(FetchProgress.PHASE_FETCHING_MANIFEST, progress.phases.get(0));
        assertEquals(FetchProgress.PHASE_UPLOADING_ASSETS, progress.phases.get(1));
        assertEquals(FetchProgress.PHASE_PERSISTING, progress.phases.get(2));

        // Asset upload called for every page in the bundle.
        verify(assets, times(2)).uploadAssets(any(), eq(resolver));

        // Final page-progress count equals total page count.
        assertEquals(2, progress.lastProcessed.get());
        assertEquals(2, progress.lastTotal.get());

        // onComplete fired with saved=true and the correct page count.
        assertNotNull(progress.completion.get());
        assertTrue(progress.completion.get().saved);
        assertEquals(2, progress.completion.get().pages);
    }

    @Test
    void performFetchAndStoreSkipsPersistWhenComponentPathNull() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);

        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        setField(spy, "cerosAssetStorageService", assets);
        when(assets.uploadAssets(any(), any())).thenReturn(Map.of());

        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        pages.put("home", parse("{}"));
        doReturn(new StoredManifestBundle("home", pages))
                .when(spy).fetchManifestBundle("https://x/manifest.json");

        RecordingProgress progress = new RecordingProgress();
        spy.performFetchAndStore("https://x/manifest.json", null, progress, mock(ResourceResolver.class));

        assertFalse(progress.phases.contains(FetchProgress.PHASE_PERSISTING));
        verify(spy, never()).storeManifestBundle(any(), anyString(), anyString(), any(), any());
        assertFalse(progress.completion.get().saved);
    }

    @Test
    void performFetchAndStorePropagatesFetchFailures() throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);
        setField(spy, "cerosAssetStorageService", mock(CerosAssetStorageService.class));
        doThrow(new IOException("bad gateway"))
                .when(spy).fetchManifestBundle("https://x/manifest.json");

        RecordingProgress progress = new RecordingProgress();
        assertThrows(IOException.class, () -> spy.performFetchAndStore(
                "https://x/manifest.json", "/content/x", progress, mock(ResourceResolver.class)));
        assertNull(progress.completion.get());
    }

    private static class RecordingProgress implements FetchProgress {
        final java.util.List<String> phases = new java.util.ArrayList<>();
        final AtomicInteger lastProcessed = new AtomicInteger(-1);
        final AtomicInteger lastTotal = new AtomicInteger(-1);
        final AtomicReference<Completion> completion = new AtomicReference<>();

        @Override public void onPhase(String phase) { phases.add(phase); }
        @Override public void onPageProgress(int processed, int total) {
            lastProcessed.set(processed);
            lastTotal.set(total);
        }
        @Override public void onComplete(String fetchedAt, boolean saved, int pages) {
            completion.set(new Completion(fetchedAt, saved, pages));
        }
        @Override public void onError(String message) {}

        static final class Completion {
            final boolean saved;
            final int pages;
            Completion(String f, boolean s, int p) { saved = s; pages = p; }
        }
    }
}
