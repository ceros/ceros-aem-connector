package com.ceros.delivery;

import com.ceros.models.cerosflex.CerosManifestV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DeliveryResult render(String manifestJson) throws IOException {
        CerosManifestV1 manifest = MAPPER.readValue(manifestJson, CerosManifestV1.class);
        DeliveryResult.Builder b = DeliveryResult.builder();
        ManifestRenderer.renderInto(b, manifest);
        return b.build();
    }

    @Test
    void inlineStyleAssetIsEmittedAsInlineStyle() throws Exception {
        // The brand-kit ships as a type="style" asset with inline CSS content.
        String json = "{"
                + "\"assets\":["
                + "  {\"type\":\"style\",\"src\":{\"type\":\"inline\","
                + "     \"content\":\":root{--color-brand-primary:#f6f6f6}\",\"mimeType\":\"text/css\"}}"
                + "],"
                + "\"deliveryModes\":{\"ssr\":{\"styles\":[{\"url\":\"https://assets.cdn.ceros.site/components.css\"}]}}"
                + "}";
        DeliveryResult result = render(json);

        assertEquals(1, result.getInlineStyles().size());
        assertTrue(result.getInlineStyles().get(0).contains("--color-brand-primary"));
        // The external SSR stylesheet is still a link, not inlined.
        assertTrue(result.getCssLinks().stream().anyMatch(c -> "https://assets.cdn.ceros.site/components.css".equals(c.getUrl())));
        assertTrue(result.isHasContent());
    }

    @Test
    void externalStyleAssetBecomesStylesheetLink() throws Exception {
        // A type="style" asset with a URL (rather than inline content) links out.
        String json = "{"
                + "\"assets\":["
                + "  {\"type\":\"style\",\"src\":{\"type\":\"external\","
                + "     \"url\":\"https://assets.cdn.ceros.site/theme.css\"}}"
                + "],"
                + "\"deliveryModes\":{\"ssr\":{\"styles\":[]}}"
                + "}";
        DeliveryResult result = render(json);

        assertTrue(result.getInlineStyles().isEmpty());
        assertTrue(result.getCssLinks().stream().anyMatch(c -> "https://assets.cdn.ceros.site/theme.css".equals(c.getUrl())));
    }

    @Test
    void webfontsPrecedeSsrStylesAndNoStyleAssetMeansNoInlineStyles() throws Exception {
        String json = "{"
                + "\"assets\":["
                + "  {\"type\":\"webfont\",\"src\":{\"type\":\"external\",\"url\":\"https://fonts.example/f.css\"}}"
                + "],"
                + "\"deliveryModes\":{\"ssr\":{\"styles\":[{\"url\":\"https://assets.cdn.ceros.site/components.css\"}]}}"
                + "}";
        DeliveryResult result = render(json);

        assertTrue(result.getInlineStyles().isEmpty());
        // Web font is prepended before the SSR stylesheet.
        assertEquals("https://fonts.example/f.css", result.getCssLinks().get(0).getUrl());
        assertEquals("https://assets.cdn.ceros.site/components.css", result.getCssLinks().get(1).getUrl());
    }

    @Test
    void customBodyHtmlIsLiftedFromDisplayMetadata() throws Exception {
        String json = "{"
                + "\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>x</p>\"}}],"
                + "\"displayMetadata\":{\"mode\":\"scale\","
                + "  \"customBodyHtml\":\"<script>window.sdkBoot=1</script>\"}"
                + "}";
        DeliveryResult result = render(json);

        assertEquals("<script>window.sdkBoot=1</script>", result.getCustomBodyHtml());
    }

    @Test
    void missingDisplayMetadataLeavesCustomBodyHtmlNull() throws Exception {
        String json = "{"
                + "\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>x</p>\"}}]"
                + "}";
        assertNull(render(json).getCustomBodyHtml());
    }

    @Test
    void customBodyHtmlAloneDoesNotMakeTheResultRenderable() throws Exception {
        // An experience with no body and no SSR mode is still not renderable,
        // even when it carries custom HTML.
        String json = "{\"displayMetadata\":{\"customBodyHtml\":\"<script>x</script>\"}}";
        DeliveryResult result = render(json);

        assertEquals("<script>x</script>", result.getCustomBodyHtml());
        assertFalse(result.isHasContent());
    }

    private static final String SDK_SCRIPT =
            "<script type=\\\"module\\\">import { connect } from '@ceros/flex-experience-sdk'</script>";

    @Test
    void sdkImportMapIsDerivedFromTheSsrScriptUrl() throws Exception {
        String json = "{"
                + "\"displayMetadata\":{\"customBodyHtml\":\"" + SDK_SCRIPT + "\"},"
                + "\"deliveryModes\":{\"ssr\":{\"scripts\":"
                + "  [{\"url\":\"https://assets.ceros.site/js/flex-ssr.js\",\"module\":true}]}}"
                + "}";
        DeliveryResult result = render(json);

        assertEquals("{\"imports\":{\"@ceros/flex-experience-sdk\":"
                        + "\"https://assets.ceros.site/js/flex-experience-sdk.js\"}}",
                result.getSdkImportMapJson());
    }

    @Test
    void sdkImportMapIgnoresQueryAndFragmentOnTheSsrScriptUrl() throws Exception {
        String json = "{"
                + "\"displayMetadata\":{\"customBodyHtml\":\"" + SDK_SCRIPT + "\"},"
                + "\"deliveryModes\":{\"ssr\":{\"scripts\":"
                + "  [{\"url\":\"https://assets.ceros.site/js/flex-ssr.js?v=2#x\"}]}}"
                + "}";
        assertEquals("{\"imports\":{\"@ceros/flex-experience-sdk\":"
                        + "\"https://assets.ceros.site/js/flex-experience-sdk.js\"}}",
                render(json).getSdkImportMapJson());
    }

    @Test
    void noImportMapWhenTheCustomHtmlDoesNotImportTheSdk() throws Exception {
        // A document may hold only one import map, so one is emitted solely
        // when the injected HTML actually names the specifier.
        String json = "{"
                + "\"displayMetadata\":{\"customBodyHtml\":\"<script>track()</script>\"},"
                + "\"deliveryModes\":{\"ssr\":{\"scripts\":"
                + "  [{\"url\":\"https://assets.ceros.site/js/flex-ssr.js\"}]}}"
                + "}";
        assertNull(render(json).getSdkImportMapJson());
    }

    @Test
    void noImportMapWhenThereIsNoSsrScriptToDeriveFrom() throws Exception {
        String json = "{\"displayMetadata\":{\"customBodyHtml\":\"" + SDK_SCRIPT + "\"}}";
        assertNull(render(json).getSdkImportMapJson());
    }

    @Test
    void noImportMapWhenTheDerivedUrlIsNotASafeHttpUrl() throws Exception {
        // Fails closed rather than escaping: an unsafe URL could close the
        // inline script element it is interpolated into.
        String json = "{"
                + "\"displayMetadata\":{\"customBodyHtml\":\"" + SDK_SCRIPT + "\"},"
                + "\"deliveryModes\":{\"ssr\":{\"scripts\":"
                + "  [{\"url\":\"javascript:alert(1)/x.js\"}]}}"
                + "}";
        assertNull(render(json).getSdkImportMapJson());
    }

    @Test
    void noImportMapWhenTheExperienceHasNoCustomBodyHtml() throws Exception {
        String json = "{\"deliveryModes\":{\"ssr\":{\"scripts\":"
                + "[{\"url\":\"https://assets.ceros.site/js/flex-ssr.js\"}]}}}";
        assertNull(render(json).getSdkImportMapJson());
    }
}
