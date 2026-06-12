package com.ceros.models.cerosflex;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted bundle of every page-manifest for a multi-page Ceros experience.
 *
 * <p>Stored in JCR as JSON on the {@code cerosflex} component so the SSR
 * router can serve any page (driven by the {@code cer_<experience.slug>}
 * query param introduced in ceros-spark #9179) without an outbound network
 * call at render time.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredManifestBundle {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonProperty("primarySlug")
    private String primarySlug;

    @JsonProperty("pagesBySlug")
    private LinkedHashMap<String, CerosManifestV1> pagesBySlug;

    public StoredManifestBundle() {
        // Jackson
    }

    public StoredManifestBundle(String primarySlug, LinkedHashMap<String, CerosManifestV1> pagesBySlug) {
        this.primarySlug = primarySlug;
        this.pagesBySlug = pagesBySlug;
    }

    public String getPrimarySlug() {
        return primarySlug;
    }

    public Map<String, CerosManifestV1> getPagesBySlug() {
        return pagesBySlug != null ? pagesBySlug : Collections.emptyMap();
    }

    /**
     * Returns the manifest for {@code slug}, falling back to the primary page
     * when {@code slug} is blank or unknown.
     */
    public CerosManifestV1 manifestFor(String slug) {
        Map<String, CerosManifestV1> pages = getPagesBySlug();
        if (pages.isEmpty()) {
            return null;
        }
        if (slug != null && !slug.isEmpty()) {
            CerosManifestV1 m = pages.get(slug);
            if (m != null) {
                return m;
            }
        }
        return primarySlug != null ? pages.get(primarySlug) : pages.values().iterator().next();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return getPagesBySlug().isEmpty();
    }

    public String toJson() throws IOException {
        return MAPPER.writeValueAsString(this);
    }

    public static StoredManifestBundle parse(String json) throws IOException {
        if (json == null || json.isEmpty()) {
            return new StoredManifestBundle(null, new LinkedHashMap<>());
        }
        return MAPPER.readValue(json, StoredManifestBundle.class);
    }
}
