package com.ceros.models;

import com.ceros.CerosConstants;
import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.services.CerosManifestService;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sling Model for the <em>Ceros Flex</em> AEM component.
 *
 * <p>Supports three delivery modes:</p>
 * <ul>
 *   <li><strong>fetch</strong> – fetches the manifest at render time from the Ceros CDN</li>
 *   <li><strong>store</strong> – reads a pre-fetched manifest from JCR (assets already in DAM)</li>
 *   <li><strong>embed</strong> – renders a lightweight iframe embed for the experience</li>
 * </ul>
 *
 * <p>In fetch and store modes the manifest's CSS, JavaScript, and HTML body
 * are extracted and exposed to the HTL template for server-side inlining.</p>
 */
@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class CerosFlexModel {

    private static final Logger log = LoggerFactory.getLogger(CerosFlexModel.class);

    private static final Pattern MANIFEST_JSON_PATTERN =
            Pattern.compile("manifest(\\.v[0-9.]+)?\\.json$");

    // Matches an opening <a tag that carries data-flex-page-slug (i.e. an
    // SPA-router internal page link) and does not already carry x-cq-linkchecker.
    // Scoping the bypass to slug-tagged anchors keeps AEM's LinkChecker active
    // for any other anchors that happen to land in the manifest html-body.
    private static final Pattern ANCHOR_OPEN_TAG_PATTERN =
            Pattern.compile(
                    "<a\\b(?=[\\s>])(?=[^>]*\\bdata-flex-page-slug\\b)(?![^>]*\\bx-cq-linkchecker\\b)",
                    Pattern.CASE_INSENSITIVE);

    public static final String MODE_FETCH = "fetch";
    public static final String MODE_STORE = "store";
    public static final String MODE_EMBED = "embed";

    private static final String SHARED_SERVICES_SCRIPT;
    static {
        String script = "";
        try (InputStream is = CerosFlexModel.class.getResourceAsStream("ceros-shared-services.js")) {
            if (is != null) {
                script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                log.error("ceros-shared-services.js resource not found on classpath");
            }
        } catch (IOException e) {
            log.error("Failed to load ceros-shared-services.js", e);
        }
        SHARED_SERVICES_SCRIPT = script;
    }

    @ValueMapValue
    private String manifestUrl;

    @ValueMapValue
    private String cerosMode;

    @ValueMapValue
    private String cerosPrefetchedManifestJson;

    @ValueMapValue
    private String cerosPrefetchedAt;

    @SlingObject
    private Resource resource;

    @OSGiService
    private CerosManifestService cerosManifestService;

    private String htmlContent;
    private List<CssViewModel> cssLinks = Collections.emptyList();
    private List<ScriptViewModel> headScripts = Collections.emptyList();
    private List<ScriptViewModel> bodyScripts = Collections.emptyList();
    private boolean hasContent = false;
    private String experienceUrl;
    private String embedTitle;
    private String embedScriptUrl;

    @PostConstruct
    protected void init() {
        if (manifestUrl != null) {
            manifestUrl = manifestUrl.trim();
        }
        if (StringUtils.isBlank(manifestUrl)) {
            return;
        }

        if (isEmbedMode()) {
            initFromEmbed();
            return;
        }

        if (!isManifestJsonUrl(manifestUrl)) {
            if (!manifestUrl.endsWith("/")) {
                manifestUrl += "/";
            }
            manifestUrl += CerosConstants.DEFAULT_ASSET_FILE_PATH;
        }

        experienceUrl = deriveExperienceUrl(manifestUrl);

        if (isStoreMode()) {
            initFromStore();
        } else {
            initFromFetch();
        }
    }

    private static boolean isManifestJsonUrl(String url) {
        return MANIFEST_JSON_PATTERN.matcher(url).find();
    }

    private static String preserveAnchorsFromLinkChecker(String html) {
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

    static String deriveExperienceUrl(String url) {
        String result = MANIFEST_JSON_PATTERN.matcher(url).replaceFirst("");
        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void initFromEmbed() {
        experienceUrl = deriveExperienceUrl(manifestUrl);

        String path = experienceUrl;
        int lastSlash = path.lastIndexOf('/');
        embedTitle = (lastSlash >= 0 && lastSlash < path.length() - 1)
                ? path.substring(lastSlash + 1).replace('-', ' ')
                : "";

        hasContent = true;
    }

    private void initFromFetch() {
        if (cerosManifestService == null) {
            log.warn("CerosManifestService is not available — cannot fetch manifest for {}", manifestUrl);
            return;
        }
        try {
            CerosManifestV0 manifest = cerosManifestService.fetchPublicManifestFromUrl(manifestUrl);
            processManifest(manifest);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to fetch Ceros manifest from {}: {}", manifestUrl, e.getMessage(), e);
        }
    }

    private void initFromStore() {
        if (StringUtils.isBlank(cerosPrefetchedManifestJson)) {
            return;
        }
        try {
            CerosManifestV0 manifest = CerosManifestV0.parseManifest(cerosPrefetchedManifestJson);
            processManifest(manifest);
        } catch (IOException e) {
            log.error("Failed to deserialize stored Ceros manifest for {}: {}", manifestUrl, e.getMessage(), e);
        }
    }

    private void processManifest(CerosManifestV0 manifest) {
        htmlContent = preserveAnchorsFromLinkChecker(manifest.getHtmlBodyContent());

        CerosManifestV0.DeliveryMode ssr = manifest.getDeliveryMode("ssr");
        if (ssr != null) {
            List<CssViewModel> css = new ArrayList<>();
            for (CerosManifestV0.Style style : ssr.getStyles()) {
                if (style.getUrl() != null) {
                    css.add(new CssViewModel(style.getUrl(), style.getIntegrity()));
                }
            }
            cssLinks = Collections.unmodifiableList(css);

            List<ScriptViewModel> body = new ArrayList<>();
            for (CerosManifestV0.Script script : ssr.getScripts()) {
                if (script.getUrl() != null) {
                    body.add(new ScriptViewModel(script));
                }
            }
            bodyScripts = Collections.unmodifiableList(body);
        }

        List<CssViewModel> fonts = new ArrayList<>();
        for (CerosManifestV0.AssetEntry entry : manifest.getAssets()) {
            if ("webfont".equals(entry.getType()) && entry.getSrc() != null && entry.getSrc().getUrl() != null) {
                fonts.add(new CssViewModel(entry.getSrc().getUrl(), entry.getSrc().getIntegrity()));
            }
        }
        if (!fonts.isEmpty()) {
            List<CssViewModel> combined = new ArrayList<>(fonts);
            combined.addAll(cssLinks);
            cssLinks = Collections.unmodifiableList(combined);
        }

        List<ScriptViewModel> head = new ArrayList<>();
        if (!SHARED_SERVICES_SCRIPT.isEmpty()) {
            head.add(ScriptViewModel.inlineScript(SHARED_SERVICES_SCRIPT));
        }
        for (CerosManifestV0.AssetEntry entry : manifest.getAssets()) {
            if ("script".equals(entry.getType())) {
                head.add(new ScriptViewModel(entry));
            }
        }
        headScripts = Collections.unmodifiableList(head);

        CerosManifestV0.DeliveryMode iframe = manifest.getDeliveryMode("iframe");
        if (iframe != null && !iframe.getScripts().isEmpty()) {
            embedScriptUrl = iframe.getScripts().get(0).getUrl();
        }

        hasContent = htmlContent != null || ssr != null || !headScripts.isEmpty()
                || !bodyScripts.isEmpty() || embedScriptUrl != null;
    }

    public boolean isConfigured() {
        return StringUtils.isNotBlank(manifestUrl);
    }

    public boolean isStoreMode() {
        return MODE_STORE.equals(cerosMode);
    }

    public boolean isEmbedMode() {
        return MODE_EMBED.equals(cerosMode);
    }

    public String getExperienceUrl() {
        return experienceUrl;
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public String getPagePreviewUrl() {
        if (resource == null) return null;
        String path = resource.getPath();
        int jcrIdx = path.indexOf("/jcr:content");
        if (jcrIdx > 0) {
            return path.substring(0, jcrIdx) + ".html?wcmmode=disabled";
        }
        return null;
    }

    public String getEmbedTitle() {
        return embedTitle;
    }

    public boolean isHasContent() {
        return hasContent;
    }

    public String getPrefetchedAt() {
        return cerosPrefetchedAt;
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public List<CssViewModel> getCssLinks() {
        return cssLinks;
    }

    public List<ScriptViewModel> getHeadScripts() {
        return headScripts;
    }

    public List<ScriptViewModel> getBodyScripts() {
        return bodyScripts;
    }

    public String getEmbedScriptUrl() {
        return embedScriptUrl;
    }

    public static final class CssViewModel {
        private final String url;
        private final String integrity;
        private final boolean sameOrigin;

        CssViewModel(String url, String integrity) {
            this.url = url;
            this.integrity = integrity;
            this.sameOrigin = url != null && url.startsWith("/");
        }

        public String getUrl() { return url; }
        public String getIntegrity() { return integrity; }
        public boolean isSameOrigin() { return sameOrigin; }
    }

    public static final class ScriptViewModel {
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

        ScriptViewModel(CerosManifestV0.Script script) {
            this.inline = false;
            this.src = true;
            this.scriptId = null;
            this.content = null;
            this.url = script.getUrl();
            this.integrity = script.getIntegrity();
            this.contentType = script.getMimeType();
            this.sameOrigin = script.getUrl() != null && script.getUrl().startsWith("/");
            this.module = script.isModule();
            this.loadStrategy = script.getLoadStrategy() != null ? script.getLoadStrategy() : "defer";
        }

        ScriptViewModel(String url, String integrity, boolean module) {
            this.inline = false;
            this.src = true;
            this.scriptId = null;
            this.content = null;
            this.url = url;
            this.integrity = integrity;
            this.contentType = null;
            this.sameOrigin = false;
            this.module = module;
            this.loadStrategy = "defer";
        }

        static ScriptViewModel inlineScript(String content) {
            return new ScriptViewModel(content, null);
        }

        private ScriptViewModel(String inlineContent, Void unused) {
            this.inline = true;
            this.src = false;
            this.scriptId = null;
            this.content = inlineContent;
            this.url = null;
            this.integrity = null;
            this.contentType = null;
            this.sameOrigin = false;
            this.module = false;
            this.loadStrategy = null;
        }

        ScriptViewModel(CerosManifestV0.AssetEntry entry) {
            CerosManifestV0.AssetSource assetSrc = entry.getSrc();
            this.inline = assetSrc != null && "inline".equals(assetSrc.getType());
            this.src = assetSrc != null && "external".equals(assetSrc.getType());
            this.scriptId = entry.getName();
            this.content = assetSrc != null ? assetSrc.getContent() : null;
            this.url = assetSrc != null ? assetSrc.getUrl() : null;
            this.integrity = assetSrc != null ? assetSrc.getIntegrity() : null;
            this.contentType = assetSrc != null ? assetSrc.getMimeType() : null;
            this.sameOrigin = this.src && this.url != null && this.url.startsWith("/");
            CerosManifestV0.AssetMetadata meta = entry.getMetadata();
            this.module = meta != null && meta.getModule() != null ? meta.getModule() : true;
            this.loadStrategy = meta != null && meta.getLoadStrategy() != null ? meta.getLoadStrategy() : "defer";
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
