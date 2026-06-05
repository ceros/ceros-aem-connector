package com.ceros.delivery;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryResultTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deriveExperienceUrlStripsManifestV0Suffix() {
        assertEquals("https://x.ceros.site/exp",
                DeliveryResult.deriveExperienceUrl("https://x.ceros.site/exp/manifest.v0.json"));
    }

    @Test
    void deriveExperienceUrlStripsVersionedManifestSuffix() {
        assertEquals("https://x.ceros.site/exp",
                DeliveryResult.deriveExperienceUrl("https://x.ceros.site/exp/manifest.v0.1.json"));
    }

    @Test
    void deriveExperienceUrlStripsTrailingSlash() {
        assertEquals("https://x.ceros.site/exp",
                DeliveryResult.deriveExperienceUrl("https://x.ceros.site/exp/"));
    }

    @Test
    void deriveExperienceUrlReturnsNullForNullInput() {
        assertNull(DeliveryResult.deriveExperienceUrl(null));
    }

    @Test
    void isManifestJsonUrlMatchesVersionedAndPlain() {
        assertTrue(DeliveryResult.isManifestJsonUrl("https://x/manifest.json"));
        assertTrue(DeliveryResult.isManifestJsonUrl("https://x/manifest.v0.json"));
        assertTrue(DeliveryResult.isManifestJsonUrl("https://x/manifest.v0.1.json"));
        assertFalse(DeliveryResult.isManifestJsonUrl("https://x/exp"));
        assertFalse(DeliveryResult.isManifestJsonUrl(null));
    }

    @Test
    void preserveAnchorsTagsOnlySlugAnchors() {
        String input = "<a class=\"cml-navigation\" href=\"/exp/page-2\" data-flex-page-slug=\"page-2\">go</a>"
                + "<a href=\"https://external.example/\">other</a>";
        String out = DeliveryResult.preserveAnchorsFromLinkChecker(input);

        assertTrue(out.contains("<a x-cq-linkchecker=\"skip\" class=\"cml-navigation\" href=\"/exp/page-2\" data-flex-page-slug=\"page-2\">"));
        // Second non-slug anchor not modified
        assertEquals(1, out.split("x-cq-linkchecker").length - 1);
    }

    @Test
    void preserveAnchorsIsIdempotent() {
        String input = "<a x-cq-linkchecker=\"skip\" data-flex-page-slug=\"page-2\" href=\"/x\">x</a>";
        assertEquals(input, DeliveryResult.preserveAnchorsFromLinkChecker(input));
    }

    @Test
    void preserveAnchorsHandlesNull() {
        assertNull(DeliveryResult.preserveAnchorsFromLinkChecker(null));
    }

    // ---- CssLink ----

    @Test
    void cssLinkExternalUrlIsNotSameOrigin() {
        DeliveryResult.CssLink css = new DeliveryResult.CssLink("https://cdn/style.css", "sha-x");
        assertEquals("https://cdn/style.css", css.getUrl());
        assertEquals("sha-x", css.getIntegrity());
        assertFalse(css.isSameOrigin());
    }

    @Test
    void cssLinkLocalUrlIsSameOrigin() {
        DeliveryResult.CssLink css = new DeliveryResult.CssLink("/content/dam/ceros/x.css", null);
        assertTrue(css.isSameOrigin());
    }

    // ---- ScriptRef ----

    @Test
    void scriptRefFromInlineAssetEntry() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\",\"name\":\"head-data\","
                        + "\"src\":{\"type\":\"inline\",\"content\":\"var x=1;\",\"mimeType\":\"application/json\"}}",
                CerosManifestV0.AssetEntry.class);

        DeliveryResult.ScriptRef ref = new DeliveryResult.ScriptRef(entry);

        assertTrue(ref.isInline());
        assertFalse(ref.isSrc());
        assertEquals("head-data", ref.getScriptId());
        assertEquals("var x=1;", ref.getContent());
        assertEquals("application/json", ref.getContentType());
    }

    @Test
    void scriptRefFromExternalAssetEntry() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\","
                        + "\"src\":{\"type\":\"external\",\"url\":\"https://cdn/x.js\",\"integrity\":\"sha-y\"}}",
                CerosManifestV0.AssetEntry.class);

        DeliveryResult.ScriptRef ref = new DeliveryResult.ScriptRef(entry);

        assertFalse(ref.isInline());
        assertTrue(ref.isSrc());
        assertEquals("https://cdn/x.js", ref.getUrl());
        assertEquals("sha-y", ref.getIntegrity());
        assertFalse(ref.isSameOrigin());
    }

    @Test
    void scriptRefFromLocalAssetEntryIsSameOrigin() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\",\"src\":{\"type\":\"external\",\"url\":\"/content/dam/ceros/app.js\"}}",
                CerosManifestV0.AssetEntry.class);

        assertTrue(new DeliveryResult.ScriptRef(entry).isSameOrigin());
    }

    @Test
    void scriptRefFromScript() throws Exception {
        CerosManifestV0.Script script = MAPPER.readValue(
                "{\"url\":\"https://cdn/app.js\",\"integrity\":\"sha-x\",\"module\":true,\"loadStrategy\":\"defer\"}",
                CerosManifestV0.Script.class);

        DeliveryResult.ScriptRef ref = new DeliveryResult.ScriptRef(script);

        assertTrue(ref.isSrc());
        assertEquals("https://cdn/app.js", ref.getUrl());
        assertTrue(ref.isModule());
        assertEquals("defer", ref.getLoadStrategy());
    }

    @Test
    void scriptRefScriptDefaultsLoadStrategyToDefer() throws Exception {
        CerosManifestV0.Script script = MAPPER.readValue("{\"url\":\"https://cdn/x.js\"}", CerosManifestV0.Script.class);
        assertEquals("defer", new DeliveryResult.ScriptRef(script).getLoadStrategy());
    }

    @Test
    void scriptRefScriptExplicitNonDeferLoadStrategy() throws Exception {
        CerosManifestV0.Script script = MAPPER.readValue(
                "{\"url\":\"https://cdn/x.js\",\"module\":false,\"loadStrategy\":\"async\"}",
                CerosManifestV0.Script.class);
        DeliveryResult.ScriptRef ref = new DeliveryResult.ScriptRef(script);
        assertFalse(ref.isModule());
        assertEquals("async", ref.getLoadStrategy());
    }

    @Test
    void scriptRefAssetEntryDefaultsModuleTrue() throws Exception {
        CerosManifestV0.AssetEntry entry = MAPPER.readValue(
                "{\"type\":\"script\",\"src\":{\"type\":\"external\",\"url\":\"https://cdn/x.js\"}}",
                CerosManifestV0.AssetEntry.class);
        assertTrue(new DeliveryResult.ScriptRef(entry).isModule());
    }

}
