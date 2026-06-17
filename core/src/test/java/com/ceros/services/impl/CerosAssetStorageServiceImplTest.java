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
import java.util.LinkedHashMap;
import java.util.Map;

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
        setField("mediaCdnBaseUrl", "https://media.cdn.ceros.site/");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosAssetStorageServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    // --- uploadAssets tests ---

    @Test
    void uploadAssetsReturnsEmptyMapForNoSlug() throws Exception {
        CerosManifestV1 manifest = MAPPER.readValue("{}", CerosManifestV1.class);
        Map<String, String> result = service.uploadAssets(manifest, resolver);
        assertTrue(result.isEmpty());
    }

    @Test
    void uploadAssetsWithNoAssetsInManifest() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"my-exp\",\"pageSlug\":\"page-1\"}}", CerosManifestV1.class);
        Map<String, String> result = service.uploadAssets(manifest, resolver);
        assertTrue(result.isEmpty());
        verify(resolver).commit();
    }

    @Test
    void uploadAssetsReturnsEmptyMapWhenAssetManagerUnavailable() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(null);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"my-exp\",\"pageSlug\":\"page-1\"}}", CerosManifestV1.class);
        Map<String, String> result = service.uploadAssets(manifest, resolver);
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
