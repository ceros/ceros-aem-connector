package com.ceros.models.cerosflex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class StoredManifestBundleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CerosManifestV1 parse(String json) throws Exception {
        return MAPPER.readValue(json, CerosManifestV1.class);
    }

    @Test
    void manifestForReturnsRequestedSlug() throws Exception {
        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        pages.put("page-1", parse("{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>1</p>\"}}]}"));
        pages.put("page-2", parse("{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>2</p>\"}}]}"));
        StoredManifestBundle bundle = new StoredManifestBundle("page-1", pages);

        assertEquals("<p>2</p>", bundle.manifestFor("page-2").getHtmlBodyContent());
    }

    @Test
    void manifestForFallsBackToPrimaryWhenSlugMissing() throws Exception {
        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        pages.put("page-1", parse("{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>1</p>\"}}]}"));
        pages.put("page-2", parse("{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>2</p>\"}}]}"));
        StoredManifestBundle bundle = new StoredManifestBundle("page-1", pages);

        assertEquals("<p>1</p>", bundle.manifestFor("page-unknown").getHtmlBodyContent());
        assertEquals("<p>1</p>", bundle.manifestFor(null).getHtmlBodyContent());
        assertEquals("<p>1</p>", bundle.manifestFor("").getHtmlBodyContent());
    }

    @Test
    void manifestForOnEmptyBundleReturnsNull() {
        StoredManifestBundle bundle = new StoredManifestBundle(null, new LinkedHashMap<>());
        assertNull(bundle.manifestFor("anything"));
        assertTrue(bundle.isEmpty());
    }

    @Test
    void parseRoundTripsBundleJson() throws Exception {
        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        pages.put("page-1", parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"page-1\"}}"));
        StoredManifestBundle original = new StoredManifestBundle("page-1", pages);

        StoredManifestBundle round = StoredManifestBundle.parse(original.toJson());

        assertEquals("page-1", round.getPrimarySlug());
        assertEquals(1, round.getPagesBySlug().size());
        assertEquals("e", round.manifestFor("page-1").getExperience().getSlug());
    }

    @Test
    void parseEmptyJsonReturnsEmptyBundle() throws Exception {
        StoredManifestBundle bundle = StoredManifestBundle.parse("");
        assertTrue(bundle.isEmpty());
        assertNull(bundle.manifestFor("anything"));
    }

    @Test
    void importMapSurvivesTheStoreRoundTrip() throws Exception {
        // Store and import modes persist the bundle by re-serialising the
        // parsed model, so a field the model does not carry is silently lost.
        // Without importMap on CerosManifestV1 the map would reach fetch mode
        // and vanish in the offline modes.
        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        pages.put("page-1", parse("{\"displayMetadata\":{\"customBodyHtml\":\"<p>1</p>\"},"
                + "\"importMap\":{"
                + "  \"imports\":{\"@ceros/flex-experience-sdk\":\"https://assets.ceros.site/js/sdk.js\"},"
                + "  \"integrity\":{\"https://assets.ceros.site/js/sdk.js\":\"sha384-abc\"}}}"));

        StoredManifestBundle restored =
                StoredManifestBundle.parse(new StoredManifestBundle("page-1", pages).toJson());
        CerosManifestV1 manifest = restored.manifestFor("page-1");

        assertEquals("https://assets.ceros.site/js/sdk.js",
                manifest.getImportMap().get("imports").get("@ceros/flex-experience-sdk").asText());
        // The SRI section round-trips too, so stored deliveries keep integrity.
        assertEquals("sha384-abc",
                manifest.getImportMap().get("integrity")
                        .get("https://assets.ceros.site/js/sdk.js").asText());
    }
}
