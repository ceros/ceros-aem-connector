package com.ceros.services.impl;

import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.FetchProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CerosManifestServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** baseUrl the mocked asset service hands back for experience slug "e". */
    private static final String BASE_URL = "https://ceros-dam.invalid/content/dam/ceros/e";

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = CerosManifestServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static CerosManifestV1 parse(String json) throws IOException {
        return MAPPER.readValue(json, CerosManifestV1.class);
    }

    /** A spy wired with a 10s timeout and a mock asset storage service. */
    private static CerosManifestServiceImpl spyWithAssets(CerosAssetStorageService assets) throws Exception {
        CerosManifestServiceImpl spy = spy(new CerosManifestServiceImpl());
        setField(spy, "httpTimeoutMillis", 10000);
        setField(spy, "cerosAssetStorageService", assets);
        return spy;
    }

    @Test
    void fetchPublicManifestFromUrlThrowsOnInvalidUrl() throws Exception {
        CerosManifestServiceImpl service = new CerosManifestServiceImpl();
        setField(service, "httpTimeoutMillis", 10000);
        assertThrows(Exception.class, () -> service.fetchPublicManifestFromUrl("not-a-url"));
    }

    @Test
    void fetchPublicManifestFromUrlTrimsUrl() throws Exception {
        CerosManifestServiceImpl service = new CerosManifestServiceImpl();
        setField(service, "httpTimeoutMillis", 10000);
        assertThrows(IOException.class,
                () -> service.fetchPublicManifestFromUrl("  https://nonexistent.invalid/manifest.json  "));
    }

    // ---- fetchManifestBundle (server-side ?baseUrl= rewrite) ----

    @Test
    void fetchManifestBundleIncludesPrimaryUnderPageSlug() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":[{\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true}]"
                + "}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        CerosManifestV1 rewritten = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"assetRewrites\":{\"baseUrl\":\"" + BASE_URL + "\",\"assets\":[]}}");
        doReturn(rewritten).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        assertEquals("home", bundle.getPrimarySlug());
        assertEquals(1, bundle.getPagesBySlug().size());
        assertSame(rewritten, bundle.manifestFor("home"));
    }

    @Test
    void fetchManifestBundleFetchesNonCurrentPages() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"}"
                + "  ]"
                + "}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        CerosManifestV1 home = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"assetRewrites\":{\"assets\":[]}}");
        CerosManifestV1 about = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"about\"},"
                + "\"assetRewrites\":{\"assets\":[]}}");
        doReturn(home).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);
        doReturn(about).when(spy).fetchRewrittenManifest("https://x/about/manifest.json", BASE_URL);

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        assertEquals(2, bundle.getPagesBySlug().size());
        assertSame(about, bundle.manifestFor("about"));
    }

    @Test
    void fetchManifestBundleSkipsPagesMissingManifestUrl() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"current\":true},"
                + "    {\"slug\":\"about\"}"
                + "  ]"
                + "}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");
        CerosManifestV1 home = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"assetRewrites\":{\"assets\":[]}}");
        doReturn(home).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        assertEquals(1, bundle.getPagesBySlug().size());
        verify(spy, times(1)).fetchRewrittenManifest(anyString(), eq(BASE_URL));
    }

    @Test
    void fetchManifestBundleSwallowsPerPageFetchErrors() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"},"
                + "    {\"slug\":\"contact\",\"manifestUrl\":\"https://x/contact/manifest.json\"}"
                + "  ]"
                + "}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        CerosManifestV1 home = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"assetRewrites\":{\"assets\":[]}}");
        CerosManifestV1 contact = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"contact\"},"
                + "\"assetRewrites\":{\"assets\":[]}}");
        doReturn(home).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);
        doThrow(new IOException("boom")).when(spy).fetchRewrittenManifest("https://x/about/manifest.json", BASE_URL);
        doReturn(contact).when(spy).fetchRewrittenManifest("https://x/contact/manifest.json", BASE_URL);

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        // Primary + contact succeeded, about was skipped.
        assertEquals(2, bundle.getPagesBySlug().size());
        assertNotNull(bundle.manifestFor("home"));
        assertNotNull(bundle.manifestFor("contact"));
        assertNull(bundle.getPagesBySlug().get("about"));
    }

    @Test
    void fetchManifestBundleSkipsSecondaryPagesWithoutRewriteData() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"}"
                + "  ]"
                + "}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        CerosManifestV1 home = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"assetRewrites\":{\"assets\":[]}}");
        // about comes back with NO assetRewrites — host didn't rewrite it; skip.
        CerosManifestV1 about = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"about\"}}");
        doReturn(home).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);
        doReturn(about).when(spy).fetchRewrittenManifest("https://x/about/manifest.json", BASE_URL);

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        assertEquals(1, bundle.getPagesBySlug().size());
        assertNull(bundle.getPagesBySlug().get("about"));
    }

    @Test
    void fetchManifestBundleThrowsWhenPrimaryHasNoRewriteData() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"pages\":[{\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true}]}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");
        // No assetRewrites on the primary → host doesn't support the rewrite.
        CerosManifestV1 home = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"}}");
        doReturn(home).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);

        assertThrows(IOException.class, () -> spy.fetchManifestBundle("https://x/home/manifest.json"));
    }

    @Test
    void fetchManifestBundleThrowsWhenExperienceSlugMissing() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{\"pages\":[]}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        assertThrows(IOException.class, () -> spy.fetchManifestBundle("https://x/home/manifest.json"));
        verify(spy, never()).fetchRewrittenManifest(anyString(), anyString());
    }

    @Test
    void fetchManifestBundlePropagatesPrimaryFetchFailure() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"pages\":[{\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true}]}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");
        doThrow(new IOException("nope")).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);

        assertThrows(IOException.class, () -> spy.fetchManifestBundle("https://x/home/manifest.json"));
    }

    @Test
    void fetchManifestBundleRestoresCleanPageUrls() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        when(assets.assetRewriteBaseUrl("e")).thenReturn(BASE_URL);
        CerosManifestServiceImpl spy = spyWithAssets(assets);

        CerosManifestV1 meta = parse("{"
                + "  \"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "  \"pages\":["
                + "    {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json\"}"
                + "  ]"
                + "}");
        doReturn(meta).when(spy).fetchPublicManifestFromUrl("https://x/home/manifest.json");

        // The server appended ?baseUrl= to the deep-link pages[].manifestUrl.
        CerosManifestV1 home = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"assetRewrites\":{\"assets\":[]},"
                + "\"pages\":["
                + "  {\"slug\":\"home\",\"manifestUrl\":\"https://x/home/manifest.json?baseUrl=foo\"},"
                + "  {\"slug\":\"about\",\"manifestUrl\":\"https://x/about/manifest.json?baseUrl=foo\"}"
                + "]}");
        CerosManifestV1 about = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"about\"},"
                + "\"assetRewrites\":{\"assets\":[]}}");
        doReturn(home).when(spy).fetchRewrittenManifest("https://x/home/manifest.json", BASE_URL);
        doReturn(about).when(spy).fetchRewrittenManifest("https://x/about/manifest.json", BASE_URL);

        StoredManifestBundle bundle = spy.fetchManifestBundle("https://x/home/manifest.json");

        CerosManifestV1 served = bundle.manifestFor("home");
        assertEquals("https://x/home/manifest.json", served.getPages().get(0).getManifestUrl());
        assertEquals("https://x/about/manifest.json", served.getPages().get(1).getManifestUrl());
    }

    // ---- performFetchAndStore ----

    @Test
    void performFetchAndStoreDrivesProgressThroughEveryPhase() throws Exception {
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        CerosManifestServiceImpl spy = spyWithAssets(assets);
        when(assets.mirrorRewrittenAssets(any(), any(), any()))
                .thenReturn(Map.of("https://cdn/a.css", "/dam/a.css"));

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

        // Asset mirror called for every page in the bundle.
        verify(assets, times(2)).mirrorRewrittenAssets(any(), eq(resolver), any());

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
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        CerosManifestServiceImpl spy = spyWithAssets(assets);
        when(assets.mirrorRewrittenAssets(any(), any(), any())).thenReturn(Map.of());

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
        CerosAssetStorageService assets = mock(CerosAssetStorageService.class);
        CerosManifestServiceImpl spy = spyWithAssets(assets);
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
