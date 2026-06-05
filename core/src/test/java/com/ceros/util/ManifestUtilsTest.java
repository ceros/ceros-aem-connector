package com.ceros.util;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ManifestUtilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CerosManifestV0 parse(String json) throws Exception {
        return MAPPER.readValue(json, CerosManifestV0.class);
    }

    @Test
    void primarySlugOfPrefersExperiencePageSlug() throws Exception {
        CerosManifestV0 manifest = parse("{\"experience\":{\"slug\":\"e\",\"pageSlug\":\"home\"},"
                + "\"pages\":[{\"slug\":\"about\",\"current\":true}]}");
        assertEquals("home", ManifestUtils.primarySlugOf(manifest));
    }

    @Test
    void primarySlugOfFallsBackToCurrentPageRef() throws Exception {
        CerosManifestV0 manifest = parse("{\"pages\":[{\"slug\":\"about\",\"current\":true}]}");
        assertEquals("about", ManifestUtils.primarySlugOf(manifest));
    }

    @Test
    void primarySlugOfReturnsNullWhenUnresolvable() throws Exception {
        CerosManifestV0 manifest = parse("{}");
        assertNull(ManifestUtils.primarySlugOf(manifest));
    }

    @Test
    void primarySlugOfReturnsNullForNullManifest() {
        assertNull(ManifestUtils.primarySlugOf(null));
    }
}
