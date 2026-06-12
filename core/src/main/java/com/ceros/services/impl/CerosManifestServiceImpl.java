package com.ceros.services.impl;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.CerosManifestService;
import com.ceros.services.FetchProgress;
import com.ceros.util.HttpUtils;
import com.ceros.util.ManifestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private CerosAssetStorageService cerosAssetStorageService;

    private int httpTimeoutMillis;
    private boolean allowHttpScheme;
    private boolean allowLocalAddresses;

    @Activate
    @Modified
    protected void activate(Config config) {
        httpTimeoutMillis = config.httpTimeoutSeconds() * 1000;
        allowHttpScheme = config.allowHttpScheme();
        allowLocalAddresses = config.allowLocalAddresses();
    }

    @Override
    public CerosManifestV0 fetchPublicManifestFromUrl(String manifestUrl) throws IOException {
        if (manifestUrl != null) {
            manifestUrl = manifestUrl.trim();
        }
        // Throws IllegalArgumentException for non-https / IP-literal / localhost
        // when the corresponding OSGi flags are off (production posture).
        HttpUtils.validateOutboundUrl(manifestUrl, allowHttpScheme, allowLocalAddresses);
        log.debug("Fetching Ceros manifest from {}", manifestUrl);

        String json = HttpUtils.fetchString(manifestUrl, httpTimeoutMillis,
                Map.of("Accept", "application/json"));
        return objectMapper.readValue(json, CerosManifestV0.class);
    }

    @Override
    public void validateManifestUrl(String manifestUrl) {
        HttpUtils.validateOutboundUrl(
                manifestUrl == null ? null : manifestUrl.trim(),
                allowHttpScheme, allowLocalAddresses);
    }

    @Override
    public StoredManifestBundle fetchManifestBundle(String manifestUrl) throws IOException {
        CerosManifestV0 primary = fetchPublicManifestFromUrl(manifestUrl);

        LinkedHashMap<String, CerosManifestV0> pages = new LinkedHashMap<>();
        String primarySlug = ManifestUtils.primarySlugOf(primary);
        pages.put(primarySlug != null ? primarySlug : "", primary);

        for (CerosManifestV0.PageRef page : primary.getPages()) {
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
        try {
            String bundleJson = bundle.toJson();
            String fetchedAt = Instant.now().toString();

            Session session = resolver.adaptTo(Session.class);
            if (session == null || !session.nodeExists(componentPath)) {
                return false;
            }

            Node node = session.getNode(componentPath);
            node.setProperty("manifestUrl", manifestUrl);
            node.setProperty("cerosMode", "store");
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
        for (CerosManifestV0 manifest : bundle.getPagesBySlug().values()) {
            urlMap.putAll(cerosAssetStorageService.uploadAssets(manifest, resolver));
            processed++;
            progress.onPageProgress(processed, total);
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
