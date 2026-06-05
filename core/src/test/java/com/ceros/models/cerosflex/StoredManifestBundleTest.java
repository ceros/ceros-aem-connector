package com.ceros.models.cerosflex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class StoredManifestBundleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CerosManifestV0 parse(String json) throws Exception {
        return MAPPER.readValue(json, CerosManifestV0.class);
    }

    @Test
    void manifestForReturnsRequestedSlug() throws Exception {
        LinkedHashMap<String, CerosManifestV0> pages = new LinkedHashMap<>();
        pages.put("page-1", parse("{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>1</p>\"}}]}"));
        pages.put("page-2", parse("{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>2</p>\"}}]}"));
        StoredManifestBundle bundle = new StoredManifestBundle("page-1", pages);

        assertEquals("<p>2</p>", bundle.manifestFor("page-2").getHtmlBodyContent());
    }

    @Test
    void manifestForFallsBackToPrimaryWhenSlugMissing() throws Exception {
        LinkedHashMap<String, CerosManifestV0> pages = new LinkedHashMap<>();
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
        LinkedHashMap<String, CerosManifestV0> pages = new LinkedHashMap<>();
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
}
