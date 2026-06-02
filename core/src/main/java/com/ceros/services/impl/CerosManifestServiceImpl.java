package com.ceros.services.impl;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.services.CerosManifestService;
import com.ceros.util.HttpUtils;
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
    public boolean storeManifest(ResourceResolver resolver, String componentPath,
                                  String manifestUrl,
                                  CerosManifestV0 manifest,
                                  Map<String, String> urlMap) {
        try {
            String manifestJson = objectMapper.writeValueAsString(manifest);
            String fetchedAt = Instant.now().toString();

            Session session = resolver.adaptTo(Session.class);
            if (session == null || !session.nodeExists(componentPath)) {
                return false;
            }

            Node node = session.getNode(componentPath);
            node.setProperty("manifestUrl", manifestUrl);
            node.setProperty("cerosMode", "store");
            node.setProperty("cerosPrefetchedManifestJson", manifestJson);
            node.setProperty("cerosPrefetchedAt", fetchedAt);

            if (!urlMap.isEmpty()) {
                node.setProperty("cerosAssetReferences",
                        urlMap.values().toArray(new String[0]));
            }

            session.save();
            log.info("Stored Ceros manifest for {} at {}", componentPath, fetchedAt);
            return true;
        } catch (Exception e) {
            log.warn("Could not save manifest to JCR at {}: {}", componentPath, e.getMessage());
            return false;
        }
    }
}
