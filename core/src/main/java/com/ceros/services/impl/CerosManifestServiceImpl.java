package com.ceros.services.impl;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.ceros.services.CerosManifestService;
import com.ceros.util.HttpUtils;
import com.ceros.util.ManifestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
