package com.ceros.services.impl;

import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.util.ArchiveUtils;
import com.ceros.util.FileUtils;
import com.ceros.util.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.io.InputStream;

import javax.jcr.Node;
import javax.jcr.Session;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    }

    private static final ObjectMapper MANIFEST_MAPPER = new ObjectMapper();

    private int httpTimeoutMillis;
    private String damBasePath;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.httpTimeoutMillis = config.httpTimeoutSeconds() * 1000;
        this.damBasePath = config.damBasePath();
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
        handleImportMapModules(manifest, assetManager, basePath, urlMap, resolver);
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

        handleArchiveImportMapModules(manifest, archive, assetManager, basePath, urlMap, resolver);

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

    /** The mutable {@code imports} object of the manifest's import map, or null. */
    private static ObjectNode importMapImports(CerosManifestV1 manifest) {
        JsonNode importMap = manifest.getImportMap();
        if (importMap == null || !importMap.isObject()) {
            return null;
        }
        JsonNode imports = importMap.get("imports");
        return imports != null && imports.isObject() ? (ObjectNode) imports : null;
    }

    /** The mutable {@code integrity} object, or null when the map carries none. */
    private static ObjectNode importMapIntegrity(CerosManifestV1 manifest) {
        JsonNode importMap = manifest.getImportMap();
        if (importMap == null || !importMap.isObject()) {
            return null;
        }
        JsonNode integrity = importMap.get("integrity");
        return integrity != null && integrity.isObject() ? (ObjectNode) integrity : null;
    }

    /** Snapshot of the specifiers, so a caller can rewrite values while iterating. */
    private static List<String> importMapSpecifiers(ObjectNode imports) {
        List<String> specifiers = new ArrayList<>();
        imports.fieldNames().forEachRemaining(specifiers::add);
        return specifiers;
    }

    /** The address for {@code specifier}, or null when it is missing or not a string. */
    private static String importMapAddress(ObjectNode imports, String specifier) {
        JsonNode value = imports.get(specifier);
        if (value == null || !value.isTextual() || StringUtils.isBlank(value.asText())) {
            return null;
        }
        return value.asText();
    }

    /**
     * Repoints one specifier at its DAM copy, carrying any integrity entry over.
     *
     * <p>SRI is keyed by resolved URL, so the hash has to move with the address.
     * The bytes are unchanged, so it still holds; leaving the old key would
     * silently drop integrity for the module.</p>
     */
    private static void repointImportMapEntry(ObjectNode imports, ObjectNode integrity,
                                              String specifier, String oldAddress, String damPath) {
        imports.put(specifier, damPath);
        if (integrity != null && integrity.has(oldAddress)) {
            integrity.set(damPath, integrity.remove(oldAddress));
        }
    }

    /**
     * Downloads the modules the import map resolves to and repoints the map at
     * the DAM copies.
     *
     * <p>Without this a stored page pulls every other asset from DAM but still
     * reaches the Ceros CDN for the SDK module, which defeats the point of the
     * mode. An entry whose download fails keeps its original URL rather than
     * pointing at a module that is not there.</p>
     */
    private void handleImportMapModules(CerosManifestV1 manifest, AssetManager assetManager,
                                        String basePath, Map<String, String> urlMap,
                                        ResourceResolver resolver) {
        ObjectNode imports = importMapImports(manifest);
        if (imports == null) {
            return;
        }
        ObjectNode integrity = importMapIntegrity(manifest);

        // Own folder, as webfonts get: these are Ceros runtime modules rather
        // than page assets, and a flat basePath could collide with an SSR
        // script that happens to share a filename.
        String modulesBasePath = basePath + "/modules";

        for (String specifier : importMapSpecifiers(imports)) {
            String url = importMapAddress(imports, specifier);
            if (url == null) {
                continue;
            }
            String damPath = modulesBasePath + "/" + FileUtils.extractFilename(url);
            uploadFile(url, damPath, "application/javascript", assetManager, urlMap, resolver);
            if (urlMap.containsKey(url)) {
                repointImportMapEntry(imports, integrity, specifier, url, damPath);
            }
        }
    }

    /**
     * Repoints the import map at the archive's own copies of the modules.
     *
     * <p>An exported bundle carries the SDK module and addresses it relatively
     * ({@code ./assets/scripts/flex-experience-sdk.js}), so its entries resolve
     * against the archive exactly like the SSR script and style URLs do —
     * {@link ArchiveUtils#normalizeLookup} absorbs the {@code ./} prefix.
     * Without the rewrite the address would resolve against the AEM page's own
     * URL rather than the DAM, and 404.</p>
     *
     * <p>An absolute address is what the export leaves behind when its own
     * download failed. Nothing in the archive matches it, so it is left alone
     * rather than logged as a missing entry.</p>
     */
    private void handleArchiveImportMapModules(CerosManifestV1 manifest, Map<String, byte[]> archive,
                                               AssetManager assetManager, String basePath,
                                               Map<String, String> urlMap, ResourceResolver resolver) {
        ObjectNode imports = importMapImports(manifest);
        if (imports == null) {
            return;
        }
        ObjectNode integrity = importMapIntegrity(manifest);

        for (String specifier : importMapSpecifiers(imports)) {
            String address = importMapAddress(imports, specifier);
            if (address == null || ABSOLUTE_URL_PATTERN.matcher(address).find()) {
                continue;
            }
            // Normalised so the key matches the archive's, which keeps the
            // catch-all below from importing the same file a second time.
            String archiveKey = ArchiveUtils.normalizeLookup(address);
            String damPath = storeArchiveEntry(archiveKey, archive, basePath,
                    "application/javascript", assetManager, urlMap, resolver);
            if (damPath != null) {
                repointImportMapEntry(imports, integrity, specifier, address, damPath);
            }
        }
    }

    private void handleWebfonts(CerosManifestV1 manifest, AssetManager assetManager,
                                 String basePath, Map<String, String> urlMap,
                                 ResourceResolver resolver) {
                                    
        Boolean cleanup = false;
        String fontsBasePath = basePath + "/fonts";

        for (CerosManifestV1.AssetEntry entry : manifest.getAssets()) {
            if ("webfont".equals(entry.getType()) && entry.getSrc() != null
                    && entry.getSrc().getUrl() != null) {

                //cleanup existing webfonts.css (generic fonts css file) for this page
                if (!cleanup) {
                    String cssDamPath = fontsBasePath + "/" + "webfonts.css";
                    createOrReplaceAsset(assetManager, cssDamPath,
                                new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), "text/css", resolver);
                    cleanup = true;
                }

                uploadWebfont(entry.getSrc().getUrl(), fontsBasePath, assetManager, urlMap, resolver);
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

    /** A scheme-qualified or protocol-relative URL, i.e. not archive-relative. */
    private static final Pattern ABSOLUTE_URL_PATTERN =
            Pattern.compile("^(?:[A-Za-z][A-Za-z0-9+.-]*:|//)");

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
                InputStream existingCssContent = assetManager.getAsset(fontsBasePath + "/" + cssFilename).getRendition("original").getStream();

                byte[] existingCssBytes = existingCssContent.readAllBytes();
                String cssContent = new String(existingCssBytes, StandardCharsets.UTF_8);
                css = cssContent + "\n" + css ;
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
