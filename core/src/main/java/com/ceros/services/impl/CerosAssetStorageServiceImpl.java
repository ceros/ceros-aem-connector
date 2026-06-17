package com.ceros.services.impl;

import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.util.ArchiveUtils;
import com.ceros.util.FileUtils;
import com.ceros.util.HttpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Session;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OSGi implementation of {@link CerosAssetStorageService}.
 *
 * <p>Downloads structured assets (CSS/JS), webfonts, and media files from the
 * Ceros CDN and uploads them to AEM DAM under a configurable base path.
 * After upload, manifest URLs are rewritten to reference the DAM copies.</p>
 */
@Component(service = CerosAssetStorageService.class)
@Designate(ocd = CerosAssetStorageServiceImpl.Config.class)
public class CerosAssetStorageServiceImpl implements CerosAssetStorageService {

    private static final Logger log = LoggerFactory.getLogger(CerosAssetStorageServiceImpl.class);

    @ObjectClassDefinition(name = "Ceros Asset Storage Service",
            description = "Downloads Ceros manifest assets and uploads them to AEM DAM")
    @interface Config {
        @AttributeDefinition(name = "HTTP timeout (seconds)",
                description = "Timeout for downloading external assets")
        int httpTimeoutSeconds() default 30;

        @AttributeDefinition(name = "DAM base path",
                description = "Root DAM folder for Ceros assets")
        String damBasePath() default "/content/dam/ceros";

        @AttributeDefinition(name = "Media CDN base URL",
                description = "Base URL for media assets embedded in HTML content (e.g. images). "
                        + "All URLs starting with this prefix will be extracted, downloaded, and uploaded to DAM.")
        String mediaCdnBaseUrl() default "https://media.cdn.ceros.site/";
    }

    private static final ObjectMapper MANIFEST_MAPPER = new ObjectMapper();

    private int httpTimeoutMillis;
    private String damBasePath;
    private String mediaCdnBaseUrl;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.httpTimeoutMillis = config.httpTimeoutSeconds() * 1000;
        this.damBasePath = config.damBasePath();
        String cdn = config.mediaCdnBaseUrl();
        if (cdn != null && !cdn.endsWith("/")) {
            cdn += "/";
        }
        this.mediaCdnBaseUrl = cdn;
    }

    @Override
    public Map<String, String> uploadAssets(CerosManifestV1 manifest, ResourceResolver resolver) throws IOException {
        String slug = manifest.getExperience() != null ? manifest.getExperience().getSlug() : null;
        if (StringUtils.isBlank(slug)) {
            log.warn("No experience slug in manifest, skipping asset upload");
            return Map.of();
        }

        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            log.warn("Could not obtain AssetManager, skipping asset upload");
            return Map.of();
        }

        String pageSlug = StringUtils.defaultIfBlank(manifest.getExperience().getPageSlug(), "page-1");
        String basePath = damBasePath + "/" + slug + "/" + pageSlug;
        Map<String, String> urlMap = new LinkedHashMap<>();

        handleDeliveryModeAssets(manifest, assetManager, basePath, urlMap, resolver);
        handleWebfonts(manifest, assetManager, basePath, urlMap, resolver);
        handleMedia(manifest, assetManager, basePath, urlMap, resolver);

        resolver.commit();

        rewriteInlineContent(manifest, urlMap);
        return urlMap;
    }

    @Override
    public Map<String, String> uploadAssetsFromArchive(CerosManifestV1 manifest,
                                                       Map<String, byte[]> archive,
                                                       ResourceResolver resolver) throws IOException {
        String slug = manifest.getExperience() != null ? manifest.getExperience().getSlug() : null;
        if (StringUtils.isBlank(slug)) {
            log.warn("No experience slug in manifest, skipping archive asset upload");
            return Map.of();
        }

        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            log.warn("Could not obtain AssetManager, skipping archive asset upload");
            return Map.of();
        }

        String pageSlug = StringUtils.defaultIfBlank(manifest.getExperience().getPageSlug(), "page-1");
        String basePath = damBasePath + "/" + slug + "/" + pageSlug;
        Map<String, String> urlMap = new LinkedHashMap<>();

        // SSR delivery mode is what store/import render — pull its CSS + JS from the archive.
        CerosManifestV1.DeliveryMode ssr = manifest.getDeliveryMode("ssr");
        if (ssr != null) {
            for (CerosManifestV1.Style style : ssr.getStyles()) {
                String damPath = storeArchiveEntry(style.getUrl(), archive, basePath,
                        StringUtils.defaultIfBlank(style.getMimeType(), "text/css"),
                        assetManager, urlMap, resolver);
                if (damPath != null) {
                    style.setUrl(damPath);
                }
            }
            for (CerosManifestV1.Script script : ssr.getScripts()) {
                String damPath = storeArchiveEntry(script.getUrl(), archive, basePath,
                        StringUtils.defaultIfBlank(script.getMimeType(), "application/javascript"),
                        assetManager, urlMap, resolver);
                if (damPath != null) {
                    script.setUrl(damPath);
                }
            }
        }

        // Webfonts declared with a direct file URL in the archive.
        for (CerosManifestV1.AssetEntry entry : manifest.getAssets()) {
            if ("webfont".equals(entry.getType()) && entry.getSrc() != null
                    && entry.getSrc().getUrl() != null) {
                String damPath = storeArchiveEntry(entry.getSrc().getUrl(), archive, basePath,
                        StringUtils.defaultIfBlank(entry.getSrc().getMimeType(), "application/octet-stream"),
                        assetManager, urlMap, resolver);
                if (damPath != null) {
                    entry.getSrc().setUrl(damPath);
                }
            }
        }

        // Media (images / video / posters) referenced from the inline body markup.
        Set<String> seen = new LinkedHashSet<>();
        for (CerosManifestV1.MediaEntry entry : manifest.getMedia()) {
            if (entry.getUrl() == null || !seen.add(FileUtils.stripQueryParams(entry.getUrl()))) {
                continue;
            }
            storeArchiveEntry(entry.getUrl(), archive, basePath,
                    StringUtils.defaultIfBlank(entry.getMimeType(), "application/octet-stream"),
                    assetManager, urlMap, resolver);
        }

        resolver.commit();

        rewriteInlineContent(manifest, urlMap);
        return urlMap;
    }

    /**
     * Resolves {@code relativeUrl} to bytes in the extracted archive and writes
     * them to the DAM, mirroring the archive's relative path under {@code basePath}
     * (e.g. {@code assets/styles/reset.css}). Idempotent across manifest fields
     * that reference the same asset. Returns the DAM path, or {@code null} when
     * the URL is blank or absent from the archive (left untouched in that case).
     */
    private String storeArchiveEntry(String relativeUrl, Map<String, byte[]> archive, String basePath,
                                     String mimeType, AssetManager assetManager,
                                     Map<String, String> urlMap, ResourceResolver resolver) {
        if (StringUtils.isBlank(relativeUrl)) {
            return null;
        }
        if (urlMap.containsKey(relativeUrl)) {
            return urlMap.get(relativeUrl);
        }
        byte[] bytes = ArchiveUtils.get(archive, relativeUrl);
        if (bytes == null) {
            log.warn("Archive has no entry for manifest URL '{}'; leaving it unchanged", relativeUrl);
            return null;
        }
        String damPath = basePath + "/" + ArchiveUtils.normalizeLookup(relativeUrl);
        createOrReplaceAsset(assetManager, damPath, new ByteArrayInputStream(bytes), mimeType, resolver);
        urlMap.put(relativeUrl, damPath);
        log.info("Stored archive asset: {} -> {}", relativeUrl, damPath);
        return damPath;
    }

    private void handleDeliveryModeAssets(CerosManifestV1 manifest, AssetManager assetManager,
                                            String basePath, Map<String, String> urlMap,
                                            ResourceResolver resolver) {
        CerosManifestV1.DeliveryMode ssr = manifest.getDeliveryMode("ssr");
        if (ssr == null) {
            return;
        }
        for (CerosManifestV1.Style style : ssr.getStyles()) {
            if (style.getUrl() != null) {
                String damPath = basePath + "/" + FileUtils.extractFilename(style.getUrl());
                uploadFile(style.getUrl(), damPath, "text/css", assetManager, urlMap, resolver);
                if (urlMap.containsKey(style.getUrl())) {
                    style.setUrl(damPath);
                }
            }
        }
        for (CerosManifestV1.Script script : ssr.getScripts()) {
            if (script.getUrl() != null) {
                String damPath = basePath + "/" + FileUtils.extractFilename(script.getUrl());
                uploadFile(script.getUrl(), damPath, "application/javascript", assetManager, urlMap, resolver);
                if (urlMap.containsKey(script.getUrl())) {
                    script.setUrl(damPath);
                }
            }
        }
    }

    private void handleWebfonts(CerosManifestV1 manifest, AssetManager assetManager,
                                 String basePath, Map<String, String> urlMap,
                                 ResourceResolver resolver) {
        for (CerosManifestV1.AssetEntry entry : manifest.getAssets()) {
            if ("webfont".equals(entry.getType()) && entry.getSrc() != null
                    && entry.getSrc().getUrl() != null) {
                uploadWebfont(entry.getSrc().getUrl(), basePath + "/fonts", assetManager, urlMap, resolver);
                String damPath = urlMap.get(entry.getSrc().getUrl());
                if (damPath != null) {
                    entry.getSrc().setUrl(damPath);
                }
            }
        }
    }

    private void handleMedia(CerosManifestV1 manifest, AssetManager assetManager,
                              String basePath, Map<String, String> urlMap,
                              ResourceResolver resolver) {
        Set<String> seen = new LinkedHashSet<>();
        for (CerosManifestV1.MediaEntry entry : manifest.getMedia()) {
            if (entry.getUrl() == null) {
                continue;
            }
            String baseUrl = FileUtils.stripQueryParams(entry.getUrl());
            if (!seen.add(baseUrl)) {
                continue;
            }
            String filename = StringUtils.defaultIfBlank(entry.getFilename(),
                    FileUtils.extractFilename(baseUrl));
            String damPath = basePath + "/media/" + filename;
            String mimeType = StringUtils.defaultIfBlank(entry.getMimeType(), "application/octet-stream");

            if (filename.endsWith(".m3u8")) {
                uploadHlsStream(baseUrl, damPath, basePath + "/media", assetManager, urlMap, resolver);
            } else {
                uploadFile(baseUrl, damPath, mimeType, assetManager, urlMap, resolver);
            }
        }
    }

    @Override
    public String uploadManifest(CerosManifestV1 manifest, ResourceResolver resolver) throws IOException {
        if (manifest == null || manifest.getExperience() == null) {
            return null;
        }
        String slug = manifest.getExperience().getSlug();
        String pageSlug = manifest.getExperience().getPageSlug();
        if (StringUtils.isBlank(slug) || StringUtils.isBlank(pageSlug)) {
            return null;
        }
        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            log.warn("Could not obtain AssetManager, skipping manifest upload for {}/{}", slug, pageSlug);
            return null;
        }

        // Deep-clone via JSON round-trip so the caller's manifest (still
        // referenced by the bundle persisted on the component) keeps its
        // original pages[].manifestUrl values — those are the CDN URLs we
        // need at render time to derive experienceUrl for the author iframe
        // preview. Only the DAM copy has its pages[] rewritten to DAM URLs.
        byte[] originalJson = MANIFEST_MAPPER.writeValueAsBytes(manifest);
        CerosManifestV1 forDam = MANIFEST_MAPPER.readValue(originalJson, CerosManifestV1.class);
        for (CerosManifestV1.PageRef page : forDam.getPages()) {
            if (StringUtils.isNotBlank(page.getSlug())) {
                page.setManifestUrl(damPathForManifest(slug, page.getSlug()));
            }
        }

        String damPath = damPathForManifest(slug, pageSlug);
        byte[] json = MANIFEST_MAPPER.writeValueAsBytes(forDam);
        createOrReplaceAsset(assetManager, damPath,
                new ByteArrayInputStream(json), "application/json", resolver);
        resolver.commit();
        log.info("Uploaded manifest to DAM: {}", damPath);
        return damPath;
    }

    @Override
    public String damPathForManifest(String experienceSlug, String pageSlug) {
        return damBasePath + "/" + experienceSlug + "/" + pageSlug + "/manifest.json";
    }

    private void createOrReplaceAsset(AssetManager assetManager, String path,
                                       InputStream inputStream, String mimeType,
                                       ResourceResolver resolver) {
        Asset asset = assetManager.assetExists(path)
                ? assetManager.getAsset(path)
                : assetManager.createAsset(path);
        asset.setRendition("original", inputStream, Map.of("jcr:mimeType", mimeType));

        // Granite setRendition doesn't persist jcr:mimeType on the rendition's jcr:content node
        try {
            Session session = resolver.adaptTo(Session.class);
            String renditionPath = path + "/jcr:content/renditions/original/jcr:content";
            if (session.nodeExists(renditionPath)) {
                session.getNode(renditionPath).setProperty("jcr:mimeType", mimeType);
            }
            String metadataPath = path + "/jcr:content/metadata";
            if (session.nodeExists(metadataPath)) {
                session.getNode(metadataPath).setProperty("dc:format", mimeType);
            }
        } catch (Exception e) {
            log.warn("Could not set MIME type for {}: {}", path, e.getMessage());
        }
    }

    private void uploadFile(String url, String damPath, String mimeType, AssetManager assetManager,
                             Map<String, String> urlMap, ResourceResolver resolver) {
        try {
            try (InputStream stream = HttpUtils.downloadStream(url, httpTimeoutMillis)) {
                createOrReplaceAsset(assetManager, damPath, stream, mimeType, resolver);
            }
            urlMap.put(url, damPath);
            log.info("Uploaded to DAM: {} -> {}", url, damPath);
        } catch (Exception e) {
            log.warn("Failed to upload {}: {}", url, e.getMessage());
        }
    }

    private static final Pattern FONT_URL_PATTERN = Pattern.compile("url\\(([^)]+)\\)");
    private static final String WOFF2_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";

    private void uploadWebfont(String cssUrl, String fontsBasePath, AssetManager assetManager,
                                Map<String, String> urlMap, ResourceResolver resolver) {
        try {
            String css = HttpUtils.fetchString(cssUrl, httpTimeoutMillis,
                    Map.of("User-Agent", WOFF2_USER_AGENT));

            Matcher matcher = FONT_URL_PATTERN.matcher(css);
            Set<String> fontUrls = new LinkedHashSet<>();
            while (matcher.find()) {
                fontUrls.add(matcher.group(1).trim());
            }

            for (String fontUrl : fontUrls) {
                String damPath = fontsBasePath + "/" + FileUtils.extractFilename(fontUrl);
                uploadFile(fontUrl, damPath, "application/octet-stream", assetManager, urlMap, resolver);
                if (urlMap.containsKey(fontUrl)) {
                    css = css.replace(fontUrl, urlMap.get(fontUrl));
                }
            }

            String cssFilename = FileUtils.extractFilename(cssUrl);
            if (!cssFilename.endsWith(".css")) {
                cssFilename = "webfonts.css";
            }
            String cssDamPath = fontsBasePath + "/" + cssFilename;
            createOrReplaceAsset(assetManager, cssDamPath,
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8)), "text/css", resolver);
            urlMap.put(cssUrl, cssDamPath);
            log.info("Uploaded to DAM: {} -> {}", cssUrl, cssDamPath);
        } catch (Exception e) {
            log.warn("Failed to process webfont {}: {}", cssUrl, e.getMessage());
        }
    }

    private void uploadHlsStream(String m3u8Url, String damPath, String mediaBasePath,
                                   AssetManager assetManager, Map<String, String> urlMap,
                                   ResourceResolver resolver) {
        try {
            byte[] bytes = HttpUtils.downloadBytes(m3u8Url, httpTimeoutMillis);
            createOrReplaceAsset(assetManager, damPath,
                    new ByteArrayInputStream(bytes), "application/vnd.apple.mpegurl", resolver);
            urlMap.put(m3u8Url, damPath);
            log.info("Uploaded to DAM: {} -> {}", m3u8Url, damPath);

            String m3u8Dir = m3u8Url.substring(0, m3u8Url.lastIndexOf('/') + 1);
            for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.endsWith(".m3u8")) {
                    continue;
                }
                String segmentUrl = line.startsWith("http") ? line : m3u8Dir + line;
                String segmentPath = mediaBasePath + "/" + FileUtils.extractFilename(segmentUrl);
                uploadFile(segmentUrl, segmentPath, "application/octet-stream", assetManager, urlMap, resolver);
            }
        } catch (Exception e) {
            log.warn("Failed to upload HLS stream {}: {}", m3u8Url, e.getMessage());
        }
    }

    private void rewriteInlineContent(CerosManifestV1 manifest, Map<String, String> urlMap) {
        if (urlMap.isEmpty()) {
            return;
        }

        CerosManifestV1.AssetEntry htmlBody = manifest.getHtmlBodyAsset();
        if (htmlBody != null && htmlBody.getSrc() != null
                && htmlBody.getSrc().getContent() != null) {
            String html = htmlBody.getSrc().getContent();
            for (Map.Entry<String, String> entry : urlMap.entrySet()) {
                html = html.replace(entry.getKey(), entry.getValue());
            }
            htmlBody.getSrc().setContent(html);
        }

        for (CerosManifestV1.AssetEntry entry : manifest.getAssets()) {
            if ("script".equals(entry.getType()) && entry.getSrc() != null
                    && "inline".equals(entry.getSrc().getType())
                    && entry.getSrc().getContent() != null) {
                String content = entry.getSrc().getContent();
                boolean changed = false;
                for (Map.Entry<String, String> pathEntry : urlMap.entrySet()) {
                    if (content.contains(pathEntry.getKey())) {
                        content = content.replace(pathEntry.getKey(), pathEntry.getValue());
                        changed = true;
                    }
                }
                if (changed) {
                    entry.getSrc().setContent(content);
                }
            }
        }
    }

}
