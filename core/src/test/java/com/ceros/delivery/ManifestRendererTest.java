package com.ceros.delivery;

import com.ceros.models.cerosflex.CerosManifestV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
