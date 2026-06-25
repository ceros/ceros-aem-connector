package com.ceros.services.impl;

import com.ceros.CerosConstants;
import com.ceros.delivery.DeliveryResult;
import com.ceros.delivery.modes.CerosDeliveryMode;
import com.ceros.delivery.modes.FetchDeliveryHandler;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.CerosManifestService;
import com.ceros.services.FetchProgress;
import com.ceros.util.ArchiveUtils;
import com.ceros.util.HttpUtils;
import com.ceros.util.ManifestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Session;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component(service = CerosManifestService.class)
@Designate(ocd = CerosManifestServiceImpl.Config.class)
public class CerosManifestServiceImpl implements CerosManifestService {

    private static final Logger log = LoggerFactory.getLogger(CerosManifestServiceImpl.class);

    @ObjectClassDefinition(name = "Ceros Manifest Service")
    @interface Config {
        @AttributeDefinition(name = "HTTP timeout (seconds)",
                description = "Timeout for outbound HTTP requests to the Ceros service")
        int httpTimeoutSeconds() default 30;

        @AttributeDefinition(name = "Allow http scheme",
                description = "Accept http:// manifest URLs in addition to https://. " +
                        "Intended for dev/test only; leave off in production.")
        boolean allowHttpScheme() default false;

        @AttributeDefinition(name = "Allow local addresses",
                description = "Accept manifest URLs whose host is an IP literal or " +
                        "localhost alias. Intended for dev/test only; leave off in " +
                        "production to defend against SSRF to cloud metadata services " +
                        "and internal hosts.")
        boolean allowLocalAddresses() default false;

        @AttributeDefinition(name = "Ceros-owned manifest domains",
                description = "Apex domains trusted to serve manifests and the scripts " +
                        "they reference. A pasted URL is only fetched and injected when " +
                        "the resolved manifest host exactly equals — or is a dotted " +
                        "subdomain of — one of these. Look-alikes are rejected. " +
                        "Production domains only by default; add non-production Ceros " +
                        "domains here for local/dev testing.")
        String[] cerosOwnedDomains() default {
                "ceros.site"
        };

        @AttributeDefinition(name = "Allow untrusted manifest hosts",
                description = "Skip the Ceros-owned domain whitelist (and the " +
                        "x-flex-manifest discovery step) and trust any host that " +
                        "passes the SSRF policy. Intended for dev/test where manifests " +
                        "are served from localhost; leave off in production so only " +
                        "Ceros-owned manifests are ever fetched and injected.")
        boolean allowUntrustedManifestHost() default false;
    }

    /** Zip-bomb guard: cap on the summed uncompressed size of an import archive. */
    private static final long MAX_ARCHIVE_UNCOMPRESSED_BYTES = 250L * 1024 * 1024;

    private static final String INDEX_MANIFEST_NAME = "index.manifest.v1.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private CerosAssetStorageService cerosAssetStorageService;

    private int httpTimeoutMillis;
    private boolean allowHttpScheme;
    private boolean allowLocalAddresses;
    private boolean allowUntrustedManifestHost;
    private List<String> cerosOwnedDomains = Arrays.asList(CerosConstants.DEFAULT_CEROS_OWNED_DOMAINS);

    @Activate
    @Modified
    protected void activate(Config config) {
        httpTimeoutMillis = config.httpTimeoutSeconds() * 1000;
        allowHttpScheme = config.allowHttpScheme();
        allowLocalAddresses = config.allowLocalAddresses();
        allowUntrustedManifestHost = config.allowUntrustedManifestHost();
        String[] domains = config.cerosOwnedDomains();
        cerosOwnedDomains = (domains != null && domains.length > 0)
                ? Arrays.asList(domains)
                : Arrays.asList(CerosConstants.DEFAULT_CEROS_OWNED_DOMAINS);
    }

    @Override
    public CerosManifestV1 fetchPublicManifestFromUrl(String manifestUrl) throws IOException {
        if (manifestUrl != null) {
            manifestUrl = manifestUrl.trim();
        }
        // Throws IllegalArgumentException for non-https / IP-literal / localhost
        // when the corresponding OSGi flags are off (production posture).
        HttpUtils.validateOutboundUrl(manifestUrl, allowHttpScheme, allowLocalAddresses);
        // Whitelist gate (defence in depth): never fetch — and so never inject
        // the scripts it references — a manifest that is not Ceros-owned, even
        // if a caller passed an unresolved or stale URL. Entry points resolve
        // vanity domains to a Ceros host up front via resolveTrustedManifestUrl.
        requireCerosOwnedManifestHost(manifestUrl);
        log.debug("Fetching Ceros manifest from {}", manifestUrl);

        String json = HttpUtils.fetchString(manifestUrl, httpTimeoutMillis,
                Map.of("Accept", "application/json"));
        return objectMapper.readValue(json, CerosManifestV1.class);
    }

    @Override
    public void validateManifestUrl(String manifestUrl) {
        HttpUtils.validateOutboundUrl(
                manifestUrl == null ? null : manifestUrl.trim(),
                allowHttpScheme, allowLocalAddresses);
    }

    @Override
    public String resolveTrustedManifestUrl(String rawUrl) throws IOException {
        String pasted = rawUrl == null ? null : rawUrl.trim();
        // SSRF gate the pasted URL before any outbound request.
        HttpUtils.validateOutboundUrl(pasted, allowHttpScheme, allowLocalAddresses);

        String manifestUrl;
        if (allowUntrustedManifestHost || HttpUtils.isUrlInAllowedDomains(pasted, cerosOwnedDomains)) {
            // Trusted host (Ceros-owned, or any host in the dev/test posture):
            // construct the manifest URL directly from the pasted experience URL.
            manifestUrl = FetchDeliveryHandler.normaliseManifestUrl(pasted);
        } else {
            // Vanity / unknown host: do not trust it. Ask the experience page to
            // advertise its canonical, Ceros-owned manifest URL via the
            // x-flex-manifest header. HEAD the experience URL (the header rides
            // the HTML page, not the .json content route).
            String experienceUrl = DeliveryResult.deriveExperienceUrl(pasted);
            Optional<String> advertised = fetchFlexManifestHeader(experienceUrl);
            manifestUrl = advertised.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
            if (manifestUrl == null) {
                throw new IllegalArgumentException(
                        "This URL isn't on a recognized Ceros domain and didn't advertise "
                                + "a Ceros manifest, so it can't be trusted.");
            }
        }

        // The advertised/constructed URL is attacker-influenced for a vanity
        // domain, so re-apply both gates before returning it: it must be a valid
        // outbound target AND served from a Ceros-owned TLD.
        HttpUtils.validateOutboundUrl(manifestUrl, allowHttpScheme, allowLocalAddresses);
        requireCerosOwnedManifestHost(manifestUrl);
        return manifestUrl;
    }

    /**
     * Reads the {@code x-flex-manifest} discovery header off a published Flex
     * experience page via a {@code HEAD} request. Extracted as a seam so the
     * vanity-domain resolution path is unit-testable without a live host.
     */
    protected Optional<String> fetchFlexManifestHeader(String experienceUrl) throws IOException {
        return HttpUtils.headResponseHeader(experienceUrl, httpTimeoutMillis,
                CerosConstants.FLEX_MANIFEST_HEADER);
    }

    /**
     * Enforces that {@code manifestUrl} is served from a Ceros-owned TLD, unless
     * the dev/test {@code allowUntrustedManifestHost} relaxation is on.
     */
    private void requireCerosOwnedManifestHost(String manifestUrl) {
        if (allowUntrustedManifestHost) {
            return;
        }
        if (!HttpUtils.isUrlInAllowedDomains(manifestUrl, cerosOwnedDomains)) {
            throw new IllegalArgumentException(
                    "Manifest host is not a recognized Ceros domain: " + manifestUrl);
        }
    }

    @Override
    public StoredManifestBundle fetchManifestBundle(String manifestUrl) throws IOException {
        CerosManifestV1 primary = fetchPublicManifestFromUrl(manifestUrl);

        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        String primarySlug = ManifestUtils.primarySlugOf(primary);
        pages.put(primarySlug != null ? primarySlug : "", primary);

        for (CerosManifestV1.PageRef page : primary.getPages()) {
            String slug = page.getSlug();
            if (slug == null || slug.isEmpty() || page.isCurrent() || pages.containsKey(slug)) {
                continue;
            }
            String pageManifestUrl = page.getManifestUrl();
            if (pageManifestUrl == null || pageManifestUrl.isEmpty()) {
                log.warn("Skipping page {}: no manifestUrl", slug);
                continue;
            }
            try {
                pages.put(slug, fetchPublicManifestFromUrl(pageManifestUrl));
            } catch (IOException | IllegalArgumentException e) {
                log.warn("Failed to fetch manifest for page {} ({}); skipping: {}",
                        slug, pageManifestUrl, e.getMessage());
            }
        }

        return new StoredManifestBundle(primarySlug, pages);
    }

    @Override
    public boolean storeManifestBundle(ResourceResolver resolver, String componentPath,
                                       String manifestUrl,
                                       StoredManifestBundle bundle,
                                       Map<String, String> urlMap) {
        return storeBundle(resolver, componentPath, manifestUrl,
                CerosDeliveryMode.STORE.value(), bundle, urlMap);
    }

    /**
     * Shared bundle-persist used by both store ({@code mode="store"}) and
     * import ({@code mode="import"}) pipelines. Writes the bundle JSON, the
     * delivery mode, the manifest URL, the fetch timestamp, and the DAM asset
     * reference list onto the component node.
     */
    private boolean storeBundle(ResourceResolver resolver, String componentPath,
                                String manifestUrl, String mode,
                                StoredManifestBundle bundle,
                                Map<String, String> urlMap) {
        try {
            String bundleJson = bundle.toJson();
            String fetchedAt = Instant.now().toString();

            Session session = resolver.adaptTo(Session.class);
            if (session == null || !session.nodeExists(componentPath)) {
                return false;
            }

            Node node = session.getNode(componentPath);
            if (manifestUrl != null) {
                node.setProperty("manifestUrl", manifestUrl);
            }
            node.setProperty("cerosMode", mode);
            node.setProperty("cerosPrefetchedManifestJson", bundleJson);
            node.setProperty("cerosPrefetchedAt", fetchedAt);

            if (urlMap != null && !urlMap.isEmpty()) {
                node.setProperty("cerosAssetReferences",
                        urlMap.values().toArray(new String[0]));
            }

            session.save();
            log.info("Stored Ceros manifest bundle for {} ({} page(s)) at {}",
                    componentPath, bundle.getPagesBySlug().size(), fetchedAt);
            return true;
        } catch (Exception e) {
            log.warn("Could not save manifest bundle to JCR at {}: {}", componentPath, e.getMessage());
            return false;
        }
    }

    @Override
    public void performFetchAndStore(String manifestUrl, String componentPath,
                                     FetchProgress progress, ResourceResolver resolver)
            throws IOException {
        progress.onPhase(FetchProgress.PHASE_FETCHING_MANIFEST);
        StoredManifestBundle bundle = fetchManifestBundle(manifestUrl);

        int total = bundle.getPagesBySlug().size();
        progress.onPhase(FetchProgress.PHASE_UPLOADING_ASSETS);
        progress.onPageProgress(0, total);

        Map<String, String> urlMap = new LinkedHashMap<>();
        int processed = 0;
        for (CerosManifestV1 manifest : bundle.getPagesBySlug().values()) {
            urlMap.putAll(cerosAssetStorageService.uploadAssets(manifest, resolver));
            processed++;
            progress.onPageProgress(processed, total);
        }

        // After every page's assets are in DAM, rewrite each manifest's
        // pages[].manifestUrl to point at the sibling DAM-stored manifests, then
        // write each manifest itself to DAM. This is what `data-flex-manifest-url`
        // resolves to in store mode — the in-browser SPA router fetches the
        // current page's manifest from DAM and uses its (now-local) pages[] for
        // sibling navigation, so a click never reaches the external CDN.
        for (CerosManifestV1 manifest : bundle.getPagesBySlug().values()) {
            String damPath = cerosAssetStorageService.uploadManifest(manifest, resolver);
            if (damPath != null) {
                urlMap.put(damPath, damPath);
            }
        }

        boolean saved = false;
        String fetchedAt = Instant.now().toString();
        if (componentPath != null) {
            progress.onPhase(FetchProgress.PHASE_PERSISTING);
            saved = storeManifestBundle(resolver, componentPath, manifestUrl, bundle, urlMap);
            if (saved) {
                fetchedAt = readFetchedAt(resolver, componentPath, fetchedAt);
            }
        }

        progress.onComplete(fetchedAt, saved, total);
    }

    @Override
    public void performImportAndStore(String archivePath, String componentPath,
                                      FetchProgress progress, ResourceResolver resolver)
            throws IOException {
        progress.onPhase(FetchProgress.PHASE_READING_ARCHIVE);
        Map<String, byte[]> archive = readArchive(archivePath, resolver);

        CerosManifestV1 primary = parsePrimaryManifest(archive);
        StoredManifestBundle bundle = buildBundleFromArchive(primary, archive);

        int total = bundle.getPagesBySlug().size();
        progress.onPhase(FetchProgress.PHASE_UPLOADING_ASSETS);
        progress.onPageProgress(0, total);

        Map<String, String> urlMap = new LinkedHashMap<>();
        int processed = 0;
        for (CerosManifestV1 manifest : bundle.getPagesBySlug().values()) {
            urlMap.putAll(cerosAssetStorageService.uploadAssetsFromArchive(manifest, archive, resolver));
            processed++;
            progress.onPageProgress(processed, total);
        }

        // Same as store mode: write each (asset-rewritten) manifest to DAM with
        // its pages[] rewritten to DAM paths, so the in-browser SPA router never
        // reaches out to a CDN.
        for (CerosManifestV1 manifest : bundle.getPagesBySlug().values()) {
            String damPath = cerosAssetStorageService.uploadManifest(manifest, resolver);
            if (damPath != null) {
                urlMap.put(damPath, damPath);
            }
        }

        // There is no CDN URL for an imported experience; point manifestUrl at
        // the primary page's DAM manifest so the component reads as configured.
        String primaryManifestUrl = damManifestUrlFor(primary);

        boolean saved = false;
        String fetchedAt = Instant.now().toString();
        if (componentPath != null) {
            progress.onPhase(FetchProgress.PHASE_PERSISTING);
            saved = storeBundle(resolver, componentPath, primaryManifestUrl,
                    CerosDeliveryMode.IMPORT.value(), bundle, urlMap);
            if (saved) {
                fetchedAt = readFetchedAt(resolver, componentPath, fetchedAt);
            }
        }

        progress.onComplete(fetchedAt, saved, total);
    }

    private Map<String, byte[]> readArchive(String archivePath, ResourceResolver resolver)
            throws IOException {
        if (archivePath == null) {
            throw new IOException("No archive path provided for import");
        }
        Resource res = resolver.getResource(archivePath);
        if (res == null) {
            throw new IOException("Uploaded archive not found at " + archivePath);
        }
        InputStream in = res.adaptTo(InputStream.class);
        if (in == null && res.getChild("jcr:content") != null) {
            in = res.getChild("jcr:content").adaptTo(InputStream.class);
        }
        if (in == null) {
            throw new IOException("Could not open uploaded archive stream at " + archivePath);
        }
        try (InputStream stream = in) {
            return ArchiveUtils.readTarGz(stream, MAX_ARCHIVE_UNCOMPRESSED_BYTES);
        }
    }

    private CerosManifestV1 parsePrimaryManifest(Map<String, byte[]> archive) throws IOException {
        byte[] indexBytes = ArchiveUtils.get(archive, INDEX_MANIFEST_NAME);
        if (indexBytes == null) {
            // Fall back to any root-level *.manifest.v1.json.
            for (Map.Entry<String, byte[]> e : archive.entrySet()) {
                if (e.getKey().endsWith(".manifest.v1.json") && !e.getKey().contains("/")) {
                    indexBytes = e.getValue();
                    break;
                }
            }
        }
        if (indexBytes == null) {
            throw new IOException("Archive does not contain " + INDEX_MANIFEST_NAME);
        }
        return objectMapper.readValue(indexBytes, CerosManifestV1.class);
    }

    private StoredManifestBundle buildBundleFromArchive(CerosManifestV1 primary,
                                                        Map<String, byte[]> archive) {
        LinkedHashMap<String, CerosManifestV1> pages = new LinkedHashMap<>();
        String primarySlug = ManifestUtils.primarySlugOf(primary);
        pages.put(primarySlug != null ? primarySlug : "", primary);

        for (CerosManifestV1.PageRef page : primary.getPages()) {
            String slug = page.getSlug();
            if (slug == null || slug.isEmpty() || page.isCurrent() || pages.containsKey(slug)) {
                continue;
            }
            byte[] pageBytes = ArchiveUtils.get(archive, page.getManifestUrl());
            if (pageBytes == null) {
                log.warn("Archive has no manifest for page {} ({}); skipping", slug, page.getManifestUrl());
                continue;
            }
            try {
                pages.put(slug, objectMapper.readValue(pageBytes, CerosManifestV1.class));
            } catch (IOException e) {
                log.warn("Failed to parse manifest for page {} ({}); skipping: {}",
                        slug, page.getManifestUrl(), e.getMessage());
            }
        }
        return new StoredManifestBundle(primarySlug, pages);
    }

    private String damManifestUrlFor(CerosManifestV1 manifest) {
        if (manifest == null || manifest.getExperience() == null) {
            return null;
        }
        CerosManifestV1.Experience exp = manifest.getExperience();
        if (exp.getSlug() == null || exp.getPageSlug() == null) {
            return null;
        }
        return cerosAssetStorageService.damPathForManifest(exp.getSlug(), exp.getPageSlug());
    }

    private String readFetchedAt(ResourceResolver resolver, String componentPath, String fallback) {
        try {
            Session session = resolver.adaptTo(Session.class);
            if (session != null && session.nodeExists(componentPath)) {
                Node node = session.getNode(componentPath);
                if (node.hasProperty("cerosPrefetchedAt")) {
                    return node.getProperty("cerosPrefetchedAt").getString();
                }
            }
        } catch (Exception e) {
            log.debug("Could not read cerosPrefetchedAt from {}: {}", componentPath, e.getMessage());
        }
        return fallback;
    }
}
