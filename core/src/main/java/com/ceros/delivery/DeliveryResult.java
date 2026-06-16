package com.ceros.delivery;

import com.ceros.delivery.modes.DeliveryHandler;
import com.ceros.models.cerosflex.CerosManifestV1;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable bag of view state a {@link DeliveryHandler} produces from its
 * inputs. The Sling Model copies these into its public getters so HTL can
 * render the component.
 */
public final class DeliveryResult {

    public static final DeliveryResult EMPTY = new Builder().build();

    private static final Pattern MANIFEST_JSON_PATTERN =
            Pattern.compile("manifest(\\.v[0-9.]+)?\\.json$");

    /**
     * Matches an opening {@code <a>} tag that carries
     * {@code data-flex-page-slug} (i.e. an SPA-router internal page link) and
     * does not already carry {@code x-cq-linkchecker}. Scoping the bypass to
     * slug-tagged anchors keeps AEM's LinkChecker active for any other
     * anchors that happen to land in the manifest html-body.
     */
    private static final Pattern ANCHOR_OPEN_TAG_PATTERN =
            Pattern.compile(
                    "<a\\b(?=[\\s>])(?=[^>]*\\bdata-flex-page-slug\\b)(?![^>]*\\bx-cq-linkchecker\\b)",
                    Pattern.CASE_INSENSITIVE);

    private final String manifestUrl;
    private final String experienceUrl;
    private final String htmlContent;
    private final List<CssLink> cssLinks;
    private final List<ScriptRef> headScripts;
    private final List<ScriptRef> bodyScripts;
    private final String embedTitle;
    private final String embedScriptUrl;
    private final String inlineScriptUrl;
    private final boolean hasContent;

    private DeliveryResult(Builder b) {
        this.manifestUrl = b.manifestUrl;
        this.experienceUrl = b.experienceUrl;
        this.htmlContent = b.htmlContent;
        this.cssLinks = Collections.unmodifiableList(new ArrayList<>(b.cssLinks));
        this.headScripts = Collections.unmodifiableList(new ArrayList<>(b.headScripts));
        this.bodyScripts = Collections.unmodifiableList(new ArrayList<>(b.bodyScripts));
        this.embedTitle = b.embedTitle;
        this.embedScriptUrl = b.embedScriptUrl;
        this.inlineScriptUrl = b.inlineScriptUrl;
        this.hasContent = b.hasContent;
    }

    public String getManifestUrl() { return manifestUrl; }
    public String getExperienceUrl() { return experienceUrl; }
    public String getHtmlContent() { return htmlContent; }
    public List<CssLink> getCssLinks() { return cssLinks; }
    public List<ScriptRef> getHeadScripts() { return headScripts; }
    public List<ScriptRef> getBodyScripts() { return bodyScripts; }
    public String getEmbedTitle() { return embedTitle; }
    public String getEmbedScriptUrl() { return embedScriptUrl; }
    public String getInlineScriptUrl() { return inlineScriptUrl; }
    public boolean isHasContent() { return hasContent; }

    /**
     * Strips trailing {@code manifest.v1.json} (and its trailing slash) so the
     * result is a usable experience root URL.
     */
    public static String deriveExperienceUrl(String url) {
        if (url == null) {
            return null;
        }
        String result = MANIFEST_JSON_PATTERN.matcher(url).replaceFirst("");
        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public static boolean isManifestJsonUrl(String url) {
        return url != null && MANIFEST_JSON_PATTERN.matcher(url).find();
    }

    public static String preserveAnchorsFromLinkChecker(String html) {
        if (html == null) {
            return null;
        }
        Matcher m = ANCHOR_OPEN_TAG_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer(html.length());
        while (m.find()) {
            m.appendReplacement(sb, "<a x-cq-linkchecker=\"skip\"");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String manifestUrl;
        private String experienceUrl;
        private String htmlContent;
        private List<CssLink> cssLinks = new ArrayList<>();
        private List<ScriptRef> headScripts = new ArrayList<>();
        private List<ScriptRef> bodyScripts = new ArrayList<>();
        private String embedTitle;
        private String embedScriptUrl;
        private String inlineScriptUrl;
        private boolean hasContent;

        public Builder manifestUrl(String v) { this.manifestUrl = v; return this; }
        public Builder experienceUrl(String v) { this.experienceUrl = v; return this; }
        public Builder htmlContent(String v) { this.htmlContent = v; return this; }
        public Builder cssLinks(List<CssLink> v) { this.cssLinks = v; return this; }
        public Builder headScripts(List<ScriptRef> v) { this.headScripts = v; return this; }
        public Builder bodyScripts(List<ScriptRef> v) { this.bodyScripts = v; return this; }
        public Builder embedTitle(String v) { this.embedTitle = v; return this; }
        public Builder embedScriptUrl(String v) { this.embedScriptUrl = v; return this; }
        public Builder inlineScriptUrl(String v) { this.inlineScriptUrl = v; return this; }
        public Builder hasContent(boolean v) { this.hasContent = v; return this; }

        public DeliveryResult build() {
            return new DeliveryResult(this);
        }
    }

    public static final class CssLink {
        private final String url;
        private final String integrity;
        private final boolean sameOrigin;

        public CssLink(String url, String integrity) {
            this.url = url;
            this.integrity = integrity;
            this.sameOrigin = url != null && url.startsWith("/");
        }

        public String getUrl() { return url; }
        public String getIntegrity() { return integrity; }
        public boolean isSameOrigin() { return sameOrigin; }
    }

    public static final class ScriptRef {
        private final boolean inline;
        private final boolean src;
        private final String scriptId;
        private final String content;
        private final String url;
        private final String integrity;
        private final String contentType;
        private final boolean sameOrigin;
        private final boolean module;
        private final String loadStrategy;

        public ScriptRef(CerosManifestV1.Script script) {
            this.inline = false;
            this.src = true;
            this.scriptId = null;
            this.content = null;
            this.url = script.getUrl();
            this.integrity = script.getIntegrity();
            this.contentType = script.getMimeType();
            this.sameOrigin = script.getUrl() != null && script.getUrl().startsWith("/");
            this.module = script.isModule();
            this.loadStrategy = StringUtils.defaultIfBlank(script.getLoadStrategy(), "defer");
        }

        public ScriptRef(CerosManifestV1.AssetEntry entry) {
            CerosManifestV1.AssetSource assetSrc = entry.getSrc();
            this.inline = assetSrc != null && "inline".equals(assetSrc.getType());
            this.src = assetSrc != null && "external".equals(assetSrc.getType());
            this.scriptId = entry.getName();
            this.content = assetSrc != null ? assetSrc.getContent() : null;
            this.url = assetSrc != null ? assetSrc.getUrl() : null;
            this.integrity = assetSrc != null ? assetSrc.getIntegrity() : null;
            this.contentType = assetSrc != null ? assetSrc.getMimeType() : null;
            this.sameOrigin = this.src && this.url != null && this.url.startsWith("/");
            CerosManifestV1.AssetMetadata meta = entry.getMetadata();
            this.module = meta == null || meta.getModule() == null || meta.getModule();
            this.loadStrategy = meta != null && meta.getLoadStrategy() != null
                    ? meta.getLoadStrategy() : "defer";
        }

        public boolean isInline() { return inline; }
        public boolean isSrc() { return src; }
        public String getScriptId() { return scriptId; }
        public String getContent() { return content; }
        public String getUrl() { return url; }
        public String getIntegrity() { return integrity; }
        public String getContentType() { return contentType; }
        public boolean isSameOrigin() { return sameOrigin; }
        public boolean isModule() { return module; }
        public String getLoadStrategy() { return loadStrategy; }
    }
}
