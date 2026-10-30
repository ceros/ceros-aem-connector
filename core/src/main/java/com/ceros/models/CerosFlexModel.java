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

    @ValueMapValue
    private String cerosInlineType;

    @ValueMapValue
    private String cerosInlineHeight;

    /**
     * The {@code flex-client.js} URL for inline mode, grabbed from the manifest
     * and persisted by {@code CerosFlexManifestUrlPostProcessor} when the dialog is
     * saved. Read at render time — no fetch on the request path.
     */
    @ValueMapValue
    private String cerosInlineScriptUrl;

    /**
     * Resource ID of the Ceros experience this component points at, derived from
     * the manifest by {@code CerosFlexManifestUrlPostProcessor} when the dialog is
     * saved. Metadata only — nothing renders it and delivery never reads it; it
     * exists so authored experiences can be found by ID across the repository.
     *
     * <p>Blank for import mode (no experience URL) and for experiences last
     * published before the manifest carried the field.</p>
     */
    @ValueMapValue
    private String cerosExperienceResourceId;

    /**
     * Whether the experience's custom Body HTML is injected alongside the
     * server-side render (SPA-7334). Read as a String rather than a boolean so
     * an absent property — every component authored before this shipped — means
     * "on", matching the decided default. Also tolerates the value being stored
     * as a Boolean, which ValueMap coerces to "true"/"false".
     */
    @ValueMapValue
    private String cerosIncludeCustomHtml;

    /**
     * Whether the experience reports to Ceros Analytics. Read as a String so an
     * absent property, which every component authored before the switch
     * existed has, can keep that component's earlier behaviour (see
     * {@link #isCerosAnalytics()}). New components get {@code true} from the
     * component's {@code cq:template}. The author's choice is only one input;
     * {@code CerosFlexView} turns it off for previews and stored copies.
     */
    @ValueMapValue
    private String cerosAnalytics;

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

    /** Resource ID of the referenced Ceros experience; {@code null} when unknown. */
    public String getExperienceResourceId() {
        return StringUtils.trimToNull(cerosExperienceResourceId);
    }

    /** Persisted {@code flex-client.js} URL for inline mode (grabbed on save). */
    public String getInlineScriptUrl() {
        return StringUtils.trimToNull(cerosInlineScriptUrl);
    }

    /**
     * True unless the author explicitly unchecked "Include custom body
     * HTML/scripts". Absent, blank, or any value other than {@code "false"}
     * means on.
     */
    public boolean isIncludeCustomHtml() {
        return !"false".equalsIgnoreCase(StringUtils.trimToEmpty(cerosIncludeCustomHtml));
    }

    /**
     * The author's "Ceros analytics tracking" choice, see
     * {@link #resolveCerosAnalytics(String, String)}.
     */
    public boolean isCerosAnalytics() {
        return resolveCerosAnalytics(cerosAnalytics, cerosMode);
    }

    /**
     * Resolves a stored {@code cerosAnalytics} value the way the dialog's
     * checkbox reads it, so the page and the dialog agree. When the property
     * is absent, the component predates the switch and keeps what it did
     * before: the iframe embed reported, while Always Fetch and Inline didn't.
     * Turning reporting on for those without anyone editing the page could
     * break a site's cookie consent setup. Any stored value other than
     * {@code "true"}, blank included, is off, as an unchecked box is.
     */
    public static boolean resolveCerosAnalytics(String storedChoice, String mode) {
        if (storedChoice == null) {
            return MODE_EMBED.equals(mode);
        }
        return "true".equalsIgnoreCase(storedChoice.trim());
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

    public String getInlineHeightAttribute() {
        if (EMBED_TYPE_SCROLLING.equals(cerosInlineType)) {
            return "height:" + StringUtils.defaultIfBlank(cerosInlineHeight, DEFAULT_EMBED_HEIGHT) + ";";
        }
        return null;
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
