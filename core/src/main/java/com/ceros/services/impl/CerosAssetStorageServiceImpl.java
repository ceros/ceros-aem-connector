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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OSGi implementation of {@link CerosAssetStorageService}.
 *
 * <p>For live "Store" mode, flex-shield owns the URL-rewriting: the manifest is
 * requested with {@code ?baseUrl=} and this service simply
 * {@linkplain #mirrorRewrittenAssets mirrors} the returned {@code assetRewrites}
 * map into the DAM (download each {@code from}, write it at its {@code path}).
 * For HTML-import mode there is no server, so the archive counterpart
 * ({@link #uploadAssetsFromArchive}) still resolves each referenced asset from
 * the extracted export and rewrites the manifest URLs in-place.</p>
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

        @AttributeDefinition(name = "Asset rewrite host",
                description = "Absolute origin (scheme + host) used to build the baseUrl "
                        + "sent to flex-shield's server-side ?baseUrl= rewrite. It must be a "
                        + "valid http(s) origin to pass server validation, but is stripped "
                        + "from the response so stored manifests keep root-relative DAM paths. "
                        + "The default uses the reserved .invalid TLD so it can never resolve.")
        String assetRewriteHost() default "https://ceros-dam.invalid";
    }

    private static final ObjectMapper MANIFEST_MAPPER = new ObjectMapper();

    private int httpTimeoutMillis;
    private String damBasePath;
    private String assetRewriteHost;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.httpTimeoutMillis = config.httpTimeoutSeconds() * 1000;
        this.damBasePath = trimTrailingSlash(config.damBasePath());
        this.assetRewriteHost = trimTrailingSlash(config.assetRewriteHost());
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String v = value;
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    @Override
    public String assetRewriteOrigin() {
        return assetRewriteHost;
    }

    @Override
    public String assetRewriteBaseUrl(String experienceSlug) {
        return assetRewriteHost + damRootFor(experienceSlug);
    }

    /** Root-relative DAM folder every asset for {@code experienceSlug} lives under. */
    private String damRootFor(String experienceSlug) {
        return damBasePath + "/" + experienceSlug;
    }

    @Override
    public Map<String, String> mirrorRewrittenAssets(CerosManifestV1 manifest,
                                                     ResourceResolver resolver,
                                                     Set<String> seenPaths) throws IOException {
        String slug = manifest.getExperience() != null ? manifest.getExperience().getSlug() : null;
        if (StringUtils.isBlank(slug)) {
            log.warn("No experience slug in manifest, skipping asset mirror");
            return Map.of();
        }
        CerosManifestV1.AssetRewrites rewrites = manifest.getAssetRewrites();
        if (rewrites == null || rewrites.getAssets().isEmpty()) {
            return Map.of();
        }

        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            log.warn("Could not obtain AssetManager, skipping asset mirror");
            return Map.of();
        }

        String damRoot = damRootFor(slug);
        Map<String, String> urlMap = new LinkedHashMap<>();
        for (CerosManifestV1.AssetRewrite asset : rewrites.getAssets()) {
            String from = asset.getFrom();
            String rel = FileUtils.safeRelativePath(asset.getPath());
            if (StringUtils.isBlank(from) || rel == null) {
                if (StringUtils.isNotBlank(from)) {
                    log.warn("Skipping rewrite asset with unsafe path '{}' (from {})", asset.getPath(), from);
                }
                continue;
            }
            if (!seenPaths.add(rel)) {
                continue; // shared bundle already mirrored by an earlier page
            }
            String damPath = damRoot + "/" + rel;
            try (InputStream stream = HttpUtils.downloadStream(from, httpTimeoutMillis)) {
                createOrReplaceAsset(assetManager, damPath, stream, mimeTypeFor(rel), resolver);
                urlMap.put(from, damPath);
                log.info("Mirrored rewrite asset: {} -> {}", from, damPath);
            } catch (Exception e) {
                log.warn("Failed to mirror rewrite asset {} -> {}: {}", from, damPath, e.getMessage());
            }
        }

        resolver.commit();
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

        // Catch-all: import every remaining file under assets/ (fonts, icons,
        // images, videos, …), mirroring the archive layout. Assets referenced
        // only from CSS url(...) or absent from the manifest's structured lists
        // are otherwise missed; mirroring the paths means relative url(...) in the
        // stored CSS still resolves against the DAM copies.
        for (Map.Entry<String, byte[]> archiveEntry : archive.entrySet()) {
            String key = archiveEntry.getKey();
            if (key.startsWith("assets/") && !urlMap.containsKey(key)) {
                storeArchiveEntry(key, archive, basePath, mimeTypeFor(key),
                        assetManager, urlMap, resolver);
            }
        }

        resolver.commit();

        rewriteInlineContent(manifest, urlMap);
        return urlMap;
    }

    /** Best-effort MIME type from a file extension, for archive assets. */
    private static String mimeTypeFor(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".otf")) return "font/otf";
        if (p.endsWith(".eot")) return "application/vnd.ms-fontobject";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".webm")) return "video/webm";
        if (p.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (p.endsWith(".ts")) return "video/mp2t";
        return "application/octet-stream";
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
