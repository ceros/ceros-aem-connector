package com.ceros.services.impl;

import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.asset.api.Rendition;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.util.HttpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.Session;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    // --- webfont compilation tests ---

    /**
     * When multiple webfont assets have a CSS URL whose filename does not end with ".css"
     * (e.g. a Google Fonts API URL whose last path segment is "css2"), every font's CSS
     * must be appended to the same "webfonts.css" DAM asset so that a single file
     * contains all @font-face declarations.
     */
    @Test
    void uploadAssetsCompilesMultipleWebfontsIntoSingleWebfontsCss() throws Exception {
        // Tracks the current content of the webfonts.css DAM asset across writes
        AtomicReference<byte[]> webfontsCssState = new AtomicReference<>(new byte[0]);

        Asset genericAsset = mock(Asset.class);
        Asset webfontsCssAsset = mock(Asset.class);
        Rendition webfontsRendition = mock(Rendition.class);

        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        lenient().when(resolver.adaptTo(Session.class)).thenReturn(null);

        when(assetManager.assetExists(anyString())).thenReturn(false);
        when(assetManager.createAsset(argThat(p -> p != null && p.endsWith("webfonts.css"))))
                .thenReturn(webfontsCssAsset);
        when(assetManager.createAsset(argThat(p -> p != null && !p.endsWith("webfonts.css"))))
                .thenReturn(genericAsset);
        when(assetManager.getAsset(argThat(p -> p != null && p.endsWith("webfonts.css"))))
                .thenReturn(webfontsCssAsset);

        // Capture each write to webfonts.css so subsequent reads return accumulated content
        doAnswer(inv -> {
            InputStream stream = inv.getArgument(1);
            webfontsCssState.set(stream.readAllBytes());
            return null;
        }).when(webfontsCssAsset).setRendition(eq("original"), any(InputStream.class), any());

        when(webfontsCssAsset.getRendition("original")).thenReturn(webfontsRendition);
        when(webfontsRendition.getStream())
                .thenAnswer(inv -> new ByteArrayInputStream(webfontsCssState.get()));

        String font1Css = "@font-face { font-family: 'Roboto'; src: url(https://cdn.example.com/roboto.woff2); }";
        String font2Css = "@font-face { font-family: 'OpenSans'; src: url(https://cdn.example.com/opensans.woff2); }";

        try (MockedStatic<HttpUtils> mockedHttp = mockStatic(HttpUtils.class)) {
            // CSS API URLs whose last path segment ("css2") does not end with ".css",
            // which triggers the webfonts.css compilation path in uploadWebfont()
            mockedHttp.when(() -> HttpUtils.fetchString(
                            eq("https://fonts.googleapis.com/css2?family=Roboto"),
                            anyInt(), any(Map.class)))
                    .thenReturn(font1Css);
            mockedHttp.when(() -> HttpUtils.fetchString(
                            eq("https://fonts.googleapis.com/css2?family=OpenSans"),
                            anyInt(), any(Map.class)))
                    .thenReturn(font2Css);
            mockedHttp.when(() -> HttpUtils.downloadStream(anyString(), anyInt()))
                    .thenAnswer(inv -> new ByteArrayInputStream(new byte[]{0x77, 0x4f, 0x46, 0x32}));

            CerosManifestV1 manifest = MAPPER.readValue(
                    "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                            + "\"assets\":["
                            + "{\"type\":\"webfont\",\"src\":{\"url\":\"https://fonts.googleapis.com/css2?family=Roboto\"}},"
                            + "{\"type\":\"webfont\",\"src\":{\"url\":\"https://fonts.googleapis.com/css2?family=OpenSans\"}}"
                            + "]}",
                    CerosManifestV1.class);

            Map<String, String> urlMap = service.uploadAssets(manifest, resolver);

            String expectedDamPath = "/content/dam/ceros/exp/page-1/fonts/webfonts.css";
            assertEquals(expectedDamPath, urlMap.get("https://fonts.googleapis.com/css2?family=Roboto"),
                    "first webfont URL must map to webfonts.css");
            assertEquals(expectedDamPath, urlMap.get("https://fonts.googleapis.com/css2?family=OpenSans"),
                    "second webfont URL must map to webfonts.css");

            String finalCss = new String(webfontsCssState.get(), StandardCharsets.UTF_8);
            assertTrue(finalCss.contains("Roboto"),
                    "webfonts.css must contain the Roboto @font-face declaration");
            assertTrue(finalCss.contains("OpenSans"),
                    "webfonts.css must contain the OpenSans @font-face declaration");
        }
    }

    // --- import map tests ---

    private static final String IMPORT_MAP_MANIFEST =
            "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                    + "\"importMap\":{"
                    + "  \"imports\":{"
                    + "    \"@ceros/flex-experience-sdk\":\"https://assets.ceros.site/js/flex-experience-sdk.js\","
                    + "    \"@ceros/flex-runtime/hls\":\"https://assets.ceros.site/js/runtime/hls.js\"},"
                    + "  \"integrity\":{"
                    + "    \"https://assets.ceros.site/js/flex-experience-sdk.js\":\"sha384-sdk\","
                    + "    \"https://assets.ceros.site/js/runtime/hls.js\":\"sha384-hls\"}}}";

    @Test
    void uploadAssetsRepointsImportMapAtTheDamCopies() throws Exception {
        Asset asset = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        lenient().when(resolver.adaptTo(Session.class)).thenReturn(null);
        when(assetManager.assetExists(anyString())).thenReturn(false);
        when(assetManager.createAsset(anyString())).thenReturn(asset);

        try (MockedStatic<HttpUtils> mockedHttp = mockStatic(HttpUtils.class)) {
            mockedHttp.when(() -> HttpUtils.downloadStream(anyString(), anyInt()))
                    .thenAnswer(inv -> new ByteArrayInputStream("export {}".getBytes(StandardCharsets.UTF_8)));

            CerosManifestV1 manifest = MAPPER.readValue(IMPORT_MAP_MANIFEST, CerosManifestV1.class);

            Map<String, String> urlMap = service.uploadAssets(manifest, resolver);

            // A stored page must reach DAM for the SDK, not the Ceros CDN.
            assertEquals("/content/dam/ceros/exp/page-1/modules/flex-experience-sdk.js",
                    urlMap.get("https://assets.ceros.site/js/flex-experience-sdk.js"));
            assertEquals("/content/dam/ceros/exp/page-1/modules/hls.js",
                    urlMap.get("https://assets.ceros.site/js/runtime/hls.js"));

            var imports = manifest.getImportMap().get("imports");
            assertEquals("/content/dam/ceros/exp/page-1/modules/flex-experience-sdk.js",
                    imports.get("@ceros/flex-experience-sdk").asText());
            assertEquals("/content/dam/ceros/exp/page-1/modules/hls.js",
                    imports.get("@ceros/flex-runtime/hls").asText());
        }
    }

    @Test
    void uploadAssetsReKeysImportMapIntegrityToTheDamPath() throws Exception {
        Asset asset = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        lenient().when(resolver.adaptTo(Session.class)).thenReturn(null);
        when(assetManager.assetExists(anyString())).thenReturn(false);
        when(assetManager.createAsset(anyString())).thenReturn(asset);

        // SRI is keyed by resolved URL. The bytes are unchanged so the hash
        // still holds, but leaving the CDN key would silently drop integrity.
        try (MockedStatic<HttpUtils> mockedHttp = mockStatic(HttpUtils.class)) {
            mockedHttp.when(() -> HttpUtils.downloadStream(anyString(), anyInt()))
                    .thenAnswer(inv -> new ByteArrayInputStream("export {}".getBytes(StandardCharsets.UTF_8)));

            CerosManifestV1 manifest = MAPPER.readValue(IMPORT_MAP_MANIFEST, CerosManifestV1.class);

            service.uploadAssets(manifest, resolver);

            var integrity = manifest.getImportMap().get("integrity");
            assertEquals("sha384-sdk",
                    integrity.get("/content/dam/ceros/exp/page-1/modules/flex-experience-sdk.js").asText());
            assertFalse(integrity.has("https://assets.ceros.site/js/flex-experience-sdk.js"),
                    "stale CDN-keyed integrity entry must not survive the rewrite");
        }
    }

    @Test
    void uploadAssetsLeavesImportMapEntryAloneWhenTheDownloadFails() throws Exception {
        Asset asset = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        lenient().when(resolver.adaptTo(Session.class)).thenReturn(null);
        when(assetManager.assetExists(anyString())).thenReturn(false);
        when(assetManager.createAsset(anyString())).thenReturn(asset);

        // Better to keep fetching that one module from the CDN than to point
        // the map at a DAM path holding nothing.
        try (MockedStatic<HttpUtils> mockedHttp = mockStatic(HttpUtils.class)) {
            mockedHttp.when(() -> HttpUtils.downloadStream(
                            eq("https://assets.ceros.site/js/flex-experience-sdk.js"), anyInt()))
                    .thenThrow(new IOException("boom"));
            mockedHttp.when(() -> HttpUtils.downloadStream(
                            eq("https://assets.ceros.site/js/runtime/hls.js"), anyInt()))
                    .thenAnswer(inv -> new ByteArrayInputStream("export {}".getBytes(StandardCharsets.UTF_8)));

            CerosManifestV1 manifest = MAPPER.readValue(IMPORT_MAP_MANIFEST, CerosManifestV1.class);

            service.uploadAssets(manifest, resolver);

            var importMap = manifest.getImportMap();
            assertEquals("https://assets.ceros.site/js/flex-experience-sdk.js",
                    importMap.get("imports").get("@ceros/flex-experience-sdk").asText());
            assertEquals("sha384-sdk",
                    importMap.get("integrity").get("https://assets.ceros.site/js/flex-experience-sdk.js").asText());
            // The entry that did upload is still repointed.
            assertEquals("/content/dam/ceros/exp/page-1/modules/hls.js",
                    importMap.get("imports").get("@ceros/flex-runtime/hls").asText());
        }
    }

    @Test
    void uploadAssetsHandlesAManifestWithNoImportMap() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"}}", CerosManifestV1.class);

        assertTrue(service.uploadAssets(manifest, resolver).isEmpty());
        assertNull(manifest.getImportMap());
    }

    // --- import map tests: HTML Import (archive) ---

    @Test
    void uploadAssetsFromArchiveRepointsImportMapAtTheArchiveCopy() throws Exception {
        // An exported bundle addresses the SDK relatively. Left alone, "./assets/..."
        // would resolve against the AEM page's own URL rather than the DAM.
        Asset asset = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        lenient().when(resolver.adaptTo(Session.class)).thenReturn(null);
        when(assetManager.assetExists(anyString())).thenReturn(false);
        when(assetManager.createAsset(anyString())).thenReturn(asset);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"importMap\":{\"imports\":{"
                        + "  \"@ceros/flex-experience-sdk\":\"./assets/scripts/flex-experience-sdk.js\"}}}",
                CerosManifestV1.class);

        Map<String, byte[]> archive = new LinkedHashMap<>();
        archive.put("assets/scripts/flex-experience-sdk.js", "export {}".getBytes());

        service.uploadAssetsFromArchive(manifest, archive, resolver);

        assertEquals("/content/dam/ceros/exp/page-1/assets/scripts/flex-experience-sdk.js",
                manifest.getImportMap().get("imports").get("@ceros/flex-experience-sdk").asText());
    }

    @Test
    void uploadAssetsFromArchiveImportsTheSdkModuleExactlyOnce() throws Exception {
        // The map's "./"-prefixed address is normalised to the archive key, so
        // the assets/ catch-all does not import the same file a second time.
        Asset asset = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        lenient().when(resolver.adaptTo(Session.class)).thenReturn(null);
        when(assetManager.assetExists(anyString())).thenReturn(false);
        when(assetManager.createAsset(anyString())).thenReturn(asset);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"importMap\":{\"imports\":{"
                        + "  \"@ceros/flex-experience-sdk\":\"./assets/scripts/flex-experience-sdk.js\"}}}",
                CerosManifestV1.class);

        Map<String, byte[]> archive = new LinkedHashMap<>();
        archive.put("assets/scripts/flex-experience-sdk.js", "export {}".getBytes());

        service.uploadAssetsFromArchive(manifest, archive, resolver);

        verify(assetManager, times(1))
                .createAsset("/content/dam/ceros/exp/page-1/assets/scripts/flex-experience-sdk.js");
    }

    @Test
    void uploadAssetsFromArchiveLeavesAnAbsoluteImportMapAddressAlone() throws Exception {
        // What the export leaves behind when its own download failed: nothing in
        // the archive matches, so there is nothing to repoint it at.
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"importMap\":{\"imports\":{"
                        + "  \"@ceros/flex-experience-sdk\":\"https://assets.ceros.site/js/flex-experience-sdk.js\"}}}",
                CerosManifestV1.class);

        service.uploadAssetsFromArchive(manifest, new LinkedHashMap<>(), resolver);

        assertEquals("https://assets.ceros.site/js/flex-experience-sdk.js",
                manifest.getImportMap().get("imports").get("@ceros/flex-experience-sdk").asText());
        verify(assetManager, never()).createAsset(anyString());
    }

    @Test
    void uploadAssetsFromArchiveLeavesImportMapEntryAloneWhenTheArchiveLacksTheModule() throws Exception {
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        CerosManifestV1 manifest = MAPPER.readValue(
                "{\"experience\":{\"slug\":\"exp\",\"pageSlug\":\"page-1\"},"
                        + "\"importMap\":{\"imports\":{"
                        + "  \"@ceros/flex-experience-sdk\":\"./assets/scripts/flex-experience-sdk.js\"}}}",
                CerosManifestV1.class);

        service.uploadAssetsFromArchive(manifest, new LinkedHashMap<>(), resolver);

        assertEquals("./assets/scripts/flex-experience-sdk.js",
                manifest.getImportMap().get("imports").get("@ceros/flex-experience-sdk").asText());
    }
}
