package com.ceros.services.impl;

import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.Session;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosAssetStorageServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private ResourceResolver resolver;
    @Mock private AssetManager assetManager;

    private CerosAssetStorageServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CerosAssetStorageServiceImpl();
        setField("httpTimeoutMillis", 5000);
        setField("damBasePath", "/content/dam/ceros");
        setField("assetRewriteHost", "https://ceros-dam.invalid");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosAssetStorageServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    // --- ?baseUrl= rewrite plumbing ---

    @Test
    void assetRewriteBaseUrlJoinsHostDamRootAndSlug() {
        assertEquals("https://ceros-dam.invalid/content/dam/ceros/my-exp",
                service.assetRewriteBaseUrl("my-exp"));
    }

    @Test
    void assetRewriteOriginIsTheConfiguredHost() {
        assertEquals("https://ceros-dam.invalid", service.assetRewriteOrigin());
    }

    // --- mirrorRewrittenAssets tests ---

    @Test
    void mirrorRewrittenAssetsReturnsEmptyForNoSlug() throws Exception {
        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"assetRewrites\":{\"assets\":[{\"from\":\"https://cdn/a.css\",\"path\":\"a.css\"}]}}",
                CerosManifestV1.class);
        Map<String, String> result = service.mirrorRewrittenAssets(manifest, resolver, new HashSet<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void mirrorRewrittenAssetsReturnsEmptyWhenNoRewriteMap() throws Exception {
        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"}}", CerosManifestV1.class);
        Map<String, String> result = service.mirrorRewrittenAssets(manifest, resolver, new HashSet<>());
        assertTrue(result.isEmpty());
        verify(resolver, never()).adaptTo(AssetManager.class);
    }

    @Test
    void mirrorRewrittenAssetsSkipsUnsafePaths() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        // Both entries carry traversal/absolute paths the safe-path guard rejects,
        // so nothing is downloaded or written — no network call in this test.
        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"assetRewrites\":{\"assets\":["
                        + "  {\"from\":\"https://cdn/a.css\",\"path\":\"../escape.css\"},"
                        + "  {\"from\":\"https://cdn/b.css\",\"path\":\"sub/has space.css\"}"
                        + "]}}",
                CerosManifestV1.class);

        Set<String> seen = new HashSet<>();
        Map<String, String> result = service.mirrorRewrittenAssets(manifest, resolver, seen);

        assertTrue(result.isEmpty());
        assertTrue(seen.isEmpty());
        verify(assetManager, never()).createAsset(anyString());
    }

    @Test
    void mirrorRewrittenAssetsDedupesAlreadySeenPaths() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"assetRewrites\":{\"assets\":["
                        + "  {\"from\":\"https://cdn/shared.js\",\"path\":\"assets/shared.js\"}"
                        + "]}}",
                CerosManifestV1.class);

        Set<String> seen = new HashSet<>();
        seen.add("assets/shared.js"); // already mirrored by an earlier page

        Map<String, String> result = service.mirrorRewrittenAssets(manifest, resolver, seen);

        assertTrue(result.isEmpty());
        verify(assetManager, never()).createAsset(anyString());
    }

    @Test
    void mirrorRewrittenAssetsReturnsEmptyWhenAssetManagerUnavailable() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(null);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"assetRewrites\":{\"assets\":[{\"from\":\"https://cdn/a.css\",\"path\":\"a.css\"}]}}",
                CerosManifestV1.class);
        Map<String, String> result = service.mirrorRewrittenAssets(manifest, resolver, new HashSet<>());
        assertTrue(result.isEmpty());
    }

    // --- uploadAssetsFromArchive tests ---

    @Test
    void uploadAssetsFromArchiveStoresAndRewritesSsrUrls() throws Exception {
        Asset asset = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        lenient().when(resolver.adaptTo(Session.class)).thenReturn(null);
        when(assetManager.assetExists(anyString())).thenReturn(false);
        when(assetManager.createAsset(anyString())).thenReturn(asset);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"deliveryModes\":{\"ssr\":{"
                        + "\"styles\":[{\"url\":\"assets/styles/reset.css\"}],"
                        + "\"scripts\":[{\"url\":\"assets/scripts/app.js\"}]}}}",
                CerosManifestV1.class);

        Map<String, byte[]> archive = new LinkedHashMap<>();
        archive.put("assets/styles/reset.css", "body{}".getBytes());
        archive.put("assets/scripts/app.js", "x=1".getBytes());

        Map<String, String> urlMap = service.uploadAssetsFromArchive(manifest, archive, resolver);

        assertEquals("/content/dam/ceros/exp/page-1/assets/styles/reset.css",
                urlMap.get("assets/styles/reset.css"));
        assertEquals("/content/dam/ceros/exp/page-1/assets/scripts/app.js",
                urlMap.get("assets/scripts/app.js"));

        // Manifest URLs are rewritten in-place to the DAM copies.
        assertEquals("/content/dam/ceros/exp/page-1/assets/styles/reset.css",
                manifest.getDeliveryMode("ssr").getStyles().get(0).getUrl());
        assertEquals("/content/dam/ceros/exp/page-1/assets/scripts/app.js",
                manifest.getDeliveryMode("ssr").getScripts().get(0).getUrl());
        verify(resolver).commit();
    }

    @Test
    void uploadAssetsFromArchiveLeavesMissingEntriesUnchanged() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"deliveryModes\":{\"ssr\":{"
                        + "\"styles\":[{\"url\":\"assets/styles/missing.css\"}]}}}",
                CerosManifestV1.class);

        Map<String, String> urlMap = service.uploadAssetsFromArchive(manifest, new LinkedHashMap<>(), resolver);

        assertFalse(urlMap.containsKey("assets/styles/missing.css"));
        assertEquals("assets/styles/missing.css",
                manifest.getDeliveryMode("ssr").getStyles().get(0).getUrl());
        verify(assetManager, never()).createAsset(anyString());
    }

    @Test
    void uploadAssetsFromArchiveReturnsEmptyForBlankSlug() throws Exception {
        CerosManifestV1 manifest = MAPPER.readValue("{}", CerosManifestV1.class);
        Map<String, String> urlMap = service.uploadAssetsFromArchive(manifest, new LinkedHashMap<>(), resolver);
        assertTrue(urlMap.isEmpty());
    }

}
