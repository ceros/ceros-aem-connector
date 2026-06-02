package com.ceros.services.impl;

import com.adobe.granite.asset.api.AssetManager;
import com.ceros.models.cerosflex.CerosManifestV0;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
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
        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        Map<String, String> result = service.uploadAssets(manifest, resolver);
        assertTrue(result.isEmpty());
    }

    @Test
    void uploadAssetsWithNoAssetsInManifest() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"my-exp\",\"pageSlug\":\"page-1\"}}", CerosManifestV0.class);
        Map<String, String> result = service.uploadAssets(manifest, resolver);
        assertTrue(result.isEmpty());
        verify(resolver).commit();
    }

    @Test
    void uploadAssetsReturnsEmptyMapWhenAssetManagerUnavailable() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(null);

        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"my-exp\",\"pageSlug\":\"page-1\"}}", CerosManifestV0.class);
        Map<String, String> result = service.uploadAssets(manifest, resolver);
        assertTrue(result.isEmpty());
    }

}
