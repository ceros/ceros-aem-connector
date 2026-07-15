package com.ceros.models;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import com.ceros.delivery.modes.CerosDeliveryMode;

/**
 * Data POJO for the <em>Ceros Flex</em> AEM component — exposes the
 * authored configuration. Delivery (manifest fetch, HTL view contract)
 * lives in {@link CerosFlexView}.
 */
@Model(adaptables = {Resource.class, SlingHttpServletRequest.class},
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class CerosFlexModel {

    public static final String MODE_FETCH = CerosDeliveryMode.FETCH.value();
    public static final String MODE_STORE = CerosDeliveryMode.STORE.value();
    public static final String MODE_IMPORT = CerosDeliveryMode.IMPORT.value();
    public static final String MODE_EMBED = CerosDeliveryMode.EMBED.value();
    public static final String MODE_INLINE = CerosDeliveryMode.INLINE.value();

    /** Iframe types — relevant only when {@link #cerosMode} is {@code embed}. */
    public static final String EMBED_TYPE_FULL_HEIGHT = "fullheight";
    public static final String EMBED_TYPE_SCROLLING = "scrolling";

    private static final String DEFAULT_EMBED_HEIGHT = "800px";

    @ValueMapValue
    private String manifestUrl;

    @ValueMapValue
    private String cerosMode;

    @ValueMapValue
    private String cerosPrefetchedManifestJson;

    @ValueMapValue
    private String cerosPrefetchedAt;

    @ValueMapValue
    private String cerosEmbedType;

    @ValueMapValue
    private String cerosEmbedHeight;

    /**
     * The {@code flex-client.js} URL for inline mode, grabbed from the manifest
     * and persisted by {@code CerosFlexManifestUrlPostProcessor} when the dialog is
     * saved. Read at render time — no fetch on the request path.
     */
    @ValueMapValue
    private String cerosInlineScriptUrl;

    @SlingObject
    private Resource resource;

    public String getManifestUrl() {
        return StringUtils.trimToNull(manifestUrl);
    }

    public String getCerosMode() {
        return cerosMode;
    }

    public String getCerosPrefetchedManifestJson() {
        return cerosPrefetchedManifestJson;
    }

    public String getPrefetchedAt() {
        return cerosPrefetchedAt;
    }

    /** Persisted {@code flex-client.js} URL for inline mode (grabbed on save). */
    public String getInlineScriptUrl() {
        return StringUtils.trimToNull(cerosInlineScriptUrl);
    }

    public boolean isConfigured() {
        if (StringUtils.isNotBlank(manifestUrl)) {
            return true;
        }
        // Import mode has no manifest URL: the experience comes from an uploaded
        // archive, and the dialog can't carry a URL (saving it would even clear a
        // server-set one). It's "configured" once the archive has been imported,
        // i.e. the prefetched bundle is present on the component.
        return isImportMode() && StringUtils.isNotBlank(cerosPrefetchedManifestJson);
    }

    public boolean isStoreMode() {
        return MODE_STORE.equals(cerosMode);
    }

    public boolean isImportMode() {
        return MODE_IMPORT.equals(cerosMode);
    }

    public boolean isEmbedMode() {
        return MODE_EMBED.equals(cerosMode);
    }

    public boolean isInlineMode() {
        return MODE_INLINE.equals(cerosMode);
    }

    /**
     * Returns the value to render into the iframe-embed snippet's
     * {@code data-embed-height} attribute. {@code "auto"} when the author
     * picked Full Height (the embed script then resizes the iframe to its
     * content); otherwise the configured CSS length (e.g. {@code "800px"}).
     */
    public String getEmbedHeightAttribute() {
        if (EMBED_TYPE_SCROLLING.equals(cerosEmbedType)) {
            return StringUtils.defaultIfBlank(cerosEmbedHeight, DEFAULT_EMBED_HEIGHT);
        }
        return "auto";
    }

    /**
     * Returns the AEM page URL (with wcmmode=disabled) for this component's
     * containing page — used by authors to preview the published render.
     */
    public String getPagePreviewUrl() {
        if (resource == null) {
            return null;
        }
        String path = resource.getPath();
        int jcrIdx = path.indexOf("/jcr:content");
        if (jcrIdx > 0) {
            return path.substring(0, jcrIdx) + ".html?wcmmode=disabled";
        }
        return null;
    }
}
