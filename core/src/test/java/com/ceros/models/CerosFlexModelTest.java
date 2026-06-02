package com.ceros.models;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.services.CerosManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosFlexModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private CerosManifestService manifestService;

    @Mock
    private Resource resource;

    private CerosFlexModel model;

    @BeforeEach
    void setUp() {
        model = new CerosFlexModel();
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosFlexModel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(model, value);
    }

    private void callInit() throws Exception {
        Method init = CerosFlexModel.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(model);
    }

    @Test
    void initWithBlankManifestUrlDoesNothing() throws Exception {
        setField("manifestUrl", "  ");
        callInit();
        assertFalse(model.isHasContent());
        assertFalse(model.isConfigured());
    }

    @Test
    void initWithNullManifestUrlDoesNothing() throws Exception {
        callInit();
        assertFalse(model.isHasContent());
        assertFalse(model.isConfigured());
    }

    @Test
    void preserveAnchorsFromLinkChecker_onlyTagsSlugAnchors() throws Exception {
        String htmlBody = "<a class=\"cml-navigation\" href=\"/exp/page-2\" data-flex-page-slug=\"page-2\">go</a>"
                + "<a href=\"https://external.example/\">other</a>";
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.json");
        setField("cerosManifestService", manifestService);
        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":"
                        + MAPPER.writeValueAsString(htmlBody) + "}}]}",
                CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        callInit();

        String out = model.getHtmlContent();
        // Slug anchor gets the skip directive
        assertTrue(out.contains("<a x-cq-linkchecker=\"skip\" class=\"cml-navigation\" href=\"/exp/page-2\" data-flex-page-slug=\"page-2\">"),
                "slug anchor should be tagged; got: " + out);
        // Non-slug anchor is left alone (only one occurrence of x-cq-linkchecker in output)
        assertEquals(1, countOccurrences(out, "x-cq-linkchecker"),
                "only the slug anchor should be tagged; got: " + out);
    }

    @Test
    void preserveAnchorsFromLinkChecker_isIdempotent() throws Exception {
        String htmlBody = "<a x-cq-linkchecker=\"skip\" data-flex-page-slug=\"page-2\" href=\"/x\">x</a>";
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.json");
        setField("cerosManifestService", manifestService);
        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":"
                        + MAPPER.writeValueAsString(htmlBody) + "}}]}",
                CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        callInit();

        assertEquals(1, countOccurrences(model.getHtmlContent(), "x-cq-linkchecker"),
                "should not double-add x-cq-linkchecker");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    void initFetchModeAppendsManifestJson() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp");
        setField("cerosManifestService", manifestService);

        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<div/>\"}}]}",
                CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.ceros.site/my-exp/manifest.v0.json"))
                .thenReturn(manifest);

        callInit();

        assertTrue(model.isHasContent());
        assertEquals("<div/>", model.getHtmlContent());
        assertEquals("https://example.ceros.site/my-exp", model.getExperienceUrl());
    }

    @Test
    void initFetchModeUrlAlreadyEndsWithManifestJson() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.json");
        setField("cerosManifestService", manifestService);

        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>hi</p>\"}}]}",
                CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.ceros.site/my-exp/manifest.json"))
                .thenReturn(manifest);

        callInit();

        assertTrue(model.isHasContent());
        assertEquals("https://example.ceros.site/my-exp", model.getExperienceUrl());
    }

    @Test
    void initFetchModeHandlesMissingService() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp");
        callInit();
        assertFalse(model.isHasContent());
    }

    @Test
    void initFetchModeHandlesIOException() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp");
        setField("cerosManifestService", manifestService);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenThrow(new IOException("timeout"));
        callInit();
        assertFalse(model.isHasContent());
    }

    @Test
    void initStoreModeDeserializesJson() throws Exception {
        String manifestJson = "{"
                + "\"deliveryModes\":{"
                + "  \"ssr\":{"
                + "    \"styles\":[{\"url\":\"/dam/style.css\",\"integrity\":\"sha-x\"}],"
                + "    \"scripts\":[{\"url\":\"/dam/app.js\"}]"
                + "  }"
                + "},"
                + "\"assets\":["
                + "  {\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<div>stored</div>\"}},"
                + "  {\"type\":\"script\",\"name\":\"head-data\",\"src\":{\"type\":\"inline\",\"content\":\"var x=1;\",\"mimeType\":\"application/json\"}}"
                + "]"
                + "}";

        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.json");
        setField("cerosMode", "store");
        setField("cerosPrefetchedManifestJson", manifestJson);

        callInit();

        assertTrue(model.isHasContent());
        assertTrue(model.isStoreMode());
        assertEquals("<div>stored</div>", model.getHtmlContent());
        assertEquals(1, model.getCssLinks().size());
        assertEquals("/dam/style.css", model.getCssLinks().get(0).getUrl());
        assertEquals("sha-x", model.getCssLinks().get(0).getIntegrity());
        assertTrue(model.getCssLinks().get(0).isSameOrigin());
        assertEquals(2, model.getHeadScripts().size());
        assertTrue(model.getHeadScripts().get(1).isInline());
        assertEquals("var x=1;", model.getHeadScripts().get(1).getContent());
        assertEquals(1, model.getBodyScripts().size());
        assertTrue(model.getBodyScripts().get(0).isSrc());
        assertEquals("/dam/app.js", model.getBodyScripts().get(0).getUrl());
    }

    @Test
    void initStoreModeHandlesBlankJson() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.json");
        setField("cerosMode", "store");
        setField("cerosPrefetchedManifestJson", "");

        callInit();
        assertFalse(model.isHasContent());
    }

    @Test
    void initStoreModeHandlesInvalidJson() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.json");
        setField("cerosMode", "store");
        setField("cerosPrefetchedManifestJson", "not-json");

        callInit();
        assertFalse(model.isHasContent());
    }

    @Test
    void initEmbedMode() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.json");
        setField("cerosMode", "embed");

        callInit();

        assertTrue(model.isHasContent());
        assertTrue(model.isEmbedMode());
        assertEquals("https://example.ceros.site/my-exp", model.getExperienceUrl());
        assertEquals("my exp", model.getEmbedTitle());
    }

    @Test
    void initEmbedModeUrlWithoutManifestJson() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/");
        setField("cerosMode", "embed");

        callInit();

        assertTrue(model.isHasContent());
        assertEquals("https://example.ceros.site/my-exp", model.getExperienceUrl());
        assertEquals("my exp", model.getEmbedTitle());
    }

    @Test
    void initEmbedModeBlankUrlDoesNotInit() throws Exception {
        setField("manifestUrl", "");
        setField("cerosMode", "embed");

        callInit();
        assertFalse(model.isHasContent());
    }

    @Test
    void isConfiguredForFetchMode() throws Exception {
        setField("manifestUrl", "https://example.com/manifest.json");
        assertTrue(model.isConfigured());
    }

    @Test
    void isConfiguredForFetchModeBlankUrl() throws Exception {
        assertFalse(model.isConfigured());
    }

    @Test
    void modeFlags() throws Exception {
        assertFalse(model.isStoreMode());
        assertFalse(model.isEmbedMode());

        setField("cerosMode", "store");
        assertTrue(model.isStoreMode());

        setField("cerosMode", "embed");
        assertTrue(model.isEmbedMode());
    }

    @Test
    void getPagePreviewUrl() throws Exception {
        setField("resource", resource);
        when(resource.getPath()).thenReturn("/content/mysite/en/jcr:content/root/cerosflex");
        assertEquals("/content/mysite/en.html?wcmmode=disabled", model.getPagePreviewUrl());
    }

    @Test
    void getPagePreviewUrlNoJcrContent() throws Exception {
        setField("resource", resource);
        when(resource.getPath()).thenReturn("/content/mysite/en/root/cerosflex");
        assertNull(model.getPagePreviewUrl());
    }

    @Test
    void getPagePreviewUrlNullResource() {
        assertNull(model.getPagePreviewUrl());
    }

    @Test
    void getPrefetchedAt() throws Exception {
        setField("cerosPrefetchedAt", "2025-01-15T10:30:00Z");
        assertEquals("2025-01-15T10:30:00Z", model.getPrefetchedAt());
    }

    @Test
    void processManifestWithNoContent() throws Exception {
        setField("cerosMode", "store");
        setField("manifestUrl", "https://example.com/manifest.json");
        setField("cerosPrefetchedManifestJson", "{\"schemaVersion\":\"0.1\"}");
        callInit();
        assertTrue(model.isHasContent());
    }

    @Test
    void processManifestHtmlOnlyNoDeliveryModes() throws Exception {
        String json = "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<div/>\"}}]}";
        setField("cerosMode", "store");
        setField("manifestUrl", "https://example.com/manifest.json");
        setField("cerosPrefetchedManifestJson", json);
        callInit();
        assertTrue(model.isHasContent());
        assertTrue(model.getCssLinks().isEmpty());
        assertEquals(1, model.getHeadScripts().size());
        assertTrue(model.getBodyScripts().isEmpty());
    }

    @Test
    void headAndBodyScriptsFromDifferentSources() throws Exception {
        String json = "{"
                + "\"deliveryModes\":{\"ssr\":{\"scripts\":["
                + "  {\"url\":\"body.js\",\"module\":true,\"loadStrategy\":\"defer\"},"
                + "  {\"url\":\"other.js\"}"
                + "]}},"
                + "\"assets\":["
                + "  {\"type\":\"script\",\"name\":\"head-config\",\"src\":{\"type\":\"inline\",\"content\":\"a\"}},"
                + "  {\"type\":\"script\",\"name\":\"public-data\",\"src\":{\"type\":\"inline\",\"content\":\"b\"}}"
                + "]"
                + "}";
        setField("cerosMode", "store");
        setField("manifestUrl", "https://example.com/manifest.json");
        setField("cerosPrefetchedManifestJson", json);
        callInit();

        assertEquals(3, model.getHeadScripts().size());
        assertEquals("head-config", model.getHeadScripts().get(1).getScriptId());
        assertEquals("public-data", model.getHeadScripts().get(2).getScriptId());
        assertEquals(2, model.getBodyScripts().size());
        assertEquals("body.js", model.getBodyScripts().get(0).getUrl());
        assertEquals("other.js", model.getBodyScripts().get(1).getUrl());
    }

    @Test
    void cssViewModelExternalUrl() {
        CerosFlexModel.CssViewModel vm = new CerosFlexModel.CssViewModel(
                "https://cdn.example.com/style.css", "sha256-abc");
        assertEquals("https://cdn.example.com/style.css", vm.getUrl());
        assertEquals("sha256-abc", vm.getIntegrity());
        assertFalse(vm.isSameOrigin());
    }

    @Test
    void cssViewModelLocalUrl() {
        CerosFlexModel.CssViewModel vm = new CerosFlexModel.CssViewModel(
                "/content/dam/ceros/style.css", null);
        assertTrue(vm.isSameOrigin());
        assertNull(vm.getIntegrity());
    }

    @Test
    void scriptViewModelFromAssetEntryInline() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\",\"name\":\"head-data\","
                + "\"src\":{\"type\":\"inline\",\"content\":\"var x=1;\",\"mimeType\":\"application/json\"}}",
                CerosManifestV0.AssetEntry.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(entry);
        assertTrue(vm.isInline());
        assertFalse(vm.isSrc());
        assertEquals("head-data", vm.getScriptId());
        assertEquals("var x=1;", vm.getContent());
        assertEquals("application/json", vm.getContentType());
        assertFalse(vm.isSameOrigin());
    }

    @Test
    void scriptViewModelFromAssetEntryExternal() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\",\"name\":\"analytics\","
                + "\"src\":{\"type\":\"external\",\"url\":\"https://cdn/analytics.js\",\"integrity\":\"sha-y\"}}",
                CerosManifestV0.AssetEntry.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(entry);
        assertFalse(vm.isInline());
        assertTrue(vm.isSrc());
        assertEquals("https://cdn/analytics.js", vm.getUrl());
        assertEquals("sha-y", vm.getIntegrity());
        assertFalse(vm.isSameOrigin());
    }

    @Test
    void scriptViewModelFromAssetEntryLocalExternal() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\",\"src\":{\"type\":\"external\",\"url\":\"/content/dam/ceros/app.js\"}}",
                CerosManifestV0.AssetEntry.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(entry);
        assertTrue(vm.isSameOrigin());
    }

    @Test
    void scriptViewModelFromScript() throws Exception {
        CerosManifestV0.Script script = MAPPER.readValue(
                "{\"url\":\"https://cdn/app.js\",\"integrity\":\"sha-x\",\"module\":true,\"loadStrategy\":\"defer\"}",
                CerosManifestV0.Script.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(script);
        assertFalse(vm.isInline());
        assertTrue(vm.isSrc());
        assertEquals("https://cdn/app.js", vm.getUrl());
        assertEquals("sha-x", vm.getIntegrity());
        assertFalse(vm.isSameOrigin());
        assertTrue(vm.isModule());
        assertEquals("defer", vm.getLoadStrategy());
    }

    @Test
    void scriptViewModelFromScriptLocal() throws Exception {
        CerosManifestV0.Script script = MAPPER.readValue(
                "{\"url\":\"/content/dam/ceros/app.js\"}",
                CerosManifestV0.Script.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(script);
        assertTrue(vm.isSameOrigin());
    }

    @Test
    void scriptViewModelModuleDefaultsTrueForAssetEntry() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\",\"src\":{\"type\":\"external\",\"url\":\"https://cdn/app.js\"}}",
                CerosManifestV0.AssetEntry.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(entry);
        assertTrue(vm.isModule());
        assertEquals("defer", vm.getLoadStrategy());
    }

    @Test
    void scriptViewModelModuleExplicitFalse() throws Exception {
        CerosManifestV0.Script script = MAPPER.readValue(
                "{\"url\":\"https://cdn/app.js\",\"module\":false,\"loadStrategy\":\"async\"}",
                CerosManifestV0.Script.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(script);
        assertFalse(vm.isModule());
        assertEquals("async", vm.getLoadStrategy());
    }

    @Test
    void scriptViewModelModuleExplicitTrue() throws Exception {
        CerosManifestV0.Script script = MAPPER.readValue(
                "{\"url\":\"https://cdn/app.js\",\"module\":true,\"loadStrategy\":\"defer\"}",
                CerosManifestV0.Script.class);
        CerosFlexModel.ScriptViewModel vm = new CerosFlexModel.ScriptViewModel(script);
        assertTrue(vm.isModule());
        assertEquals("defer", vm.getLoadStrategy());
    }

    @Test
    void cssLinksNullUrlFiltered() throws Exception {
        String json = "{\"deliveryModes\":{\"ssr\":{\"styles\":[{\"mimeType\":\"text/css\"}]}}}";
        setField("cerosMode", "store");
        setField("manifestUrl", "https://example.com/manifest.json");
        setField("cerosPrefetchedManifestJson", json);
        callInit();
        assertTrue(model.getCssLinks().isEmpty());
    }

    @Test
    void experienceUrlStripsTrailingSlash() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/");
        setField("cerosManifestService", manifestService);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(
                MAPPER.readValue("{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"x\"}}]}",
                        CerosManifestV0.class));
        callInit();
        assertEquals("https://example.ceros.site/my-exp", model.getExperienceUrl());
    }

    @Test
    void initFetchModePreservesV01ManifestUrl() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.v0.1.json");
        setField("cerosManifestService", manifestService);

        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<div>v01</div>\"}}]}",
                CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.ceros.site/my-exp/manifest.v0.1.json"))
                .thenReturn(manifest);

        callInit();

        assertTrue(model.isHasContent());
        assertEquals("<div>v01</div>", model.getHtmlContent());
        assertEquals("https://example.ceros.site/my-exp", model.getExperienceUrl());
    }

    @Test
    void initEmbedModeWithV01Url() throws Exception {
        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.v0.1.json");
        setField("cerosMode", "embed");

        callInit();

        assertTrue(model.isHasContent());
        assertEquals("https://example.ceros.site/my-exp", model.getExperienceUrl());
        assertEquals("my exp", model.getEmbedTitle());
    }

    @Test
    void initStoreModeWithV0Json() throws Exception {
        String v0Json = "{"
                + "\"schemaVersion\":\"0.1\","
                + "\"experience\":{\"slug\":\"test-exp\",\"pageSlug\":\"page-1\"},"
                + "\"deliveryModes\":{"
                + "  \"ssr\":{"
                + "    \"scripts\":[{\"url\":\"https://cdn/flex-ssr.js\",\"integrity\":\"sha384-abc\",\"module\":true,\"loadStrategy\":\"defer\"}],"
                + "    \"styles\":[{\"url\":\"https://cdn/style.css\",\"integrity\":\"sha384-def\"}]"
                + "  },"
                + "  \"iframe\":{\"scripts\":[{\"url\":\"https://cdn/embed.v1.js\",\"module\":false}],\"styles\":[]}"
                + "},"
                + "\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<div>v0 stored</div>\"}}]"
                + "}";

        setField("manifestUrl", "https://example.ceros.site/my-exp/manifest.v0.1.json");
        setField("cerosMode", "store");
        setField("cerosPrefetchedManifestJson", v0Json);

        callInit();

        assertTrue(model.isHasContent());
        assertEquals("<div>v0 stored</div>", model.getHtmlContent());
        assertEquals(1, model.getCssLinks().size());
        assertEquals("https://cdn/style.css", model.getCssLinks().get(0).getUrl());
        assertEquals("sha384-def", model.getCssLinks().get(0).getIntegrity());
        assertEquals(1, model.getBodyScripts().size());
        assertEquals("https://cdn/flex-ssr.js", model.getBodyScripts().get(0).getUrl());
        assertEquals("https://cdn/embed.v1.js", model.getEmbedScriptUrl());
    }

    @Test
    void deriveExperienceUrlFromV01ManifestUrl() {
        assertEquals("https://example.com/my-exp",
                CerosFlexModel.deriveExperienceUrl("https://example.com/my-exp/manifest.v0.1.json"));
        assertEquals("https://example.com/my-exp",
                CerosFlexModel.deriveExperienceUrl("https://example.com/my-exp/manifest.json"));
        assertEquals("https://example.com/my-exp",
                CerosFlexModel.deriveExperienceUrl("https://example.com/my-exp/manifest.v2.3.json"));
        assertEquals("https://example.com/my-exp",
                CerosFlexModel.deriveExperienceUrl("https://example.com/my-exp/"));
    }

    @Test
    void embedScriptUrlFromIframeDeliveryMode() throws Exception {
        String json = "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<div/>\"}}],"
                + "\"deliveryModes\":{\"iframe\":{\"scripts\":[{\"url\":\"https://cdn/embed.v1.js\"}]}}}";
        setField("cerosMode", "store");
        setField("manifestUrl", "https://example.com/manifest.json");
        setField("cerosPrefetchedManifestJson", json);
        callInit();
        assertEquals("https://cdn/embed.v1.js", model.getEmbedScriptUrl());
    }
}
