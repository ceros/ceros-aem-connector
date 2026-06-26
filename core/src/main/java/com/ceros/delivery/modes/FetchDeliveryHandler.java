package com.ceros.delivery.modes;

import com.ceros.CerosConstants;
import com.ceros.delivery.DeepLinkResolver;
import com.ceros.delivery.DeliveryResult;
import com.ceros.delivery.ManifestRenderer;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.services.CerosManifestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Live-fetch mode: pulls the manifest from the Ceros CDN at render time and,
 * if the deep-link query param targets a different page, fetches that page's
 * manifest instead.
 */
public final class FetchDeliveryHandler implements DeliveryHandler {

    private static final Logger log = LoggerFactory.getLogger(FetchDeliveryHandler.class);

    private final CerosManifestService manifestService;

    public FetchDeliveryHandler(CerosManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @Override
    public String mode() {
        return CerosDeliveryMode.FETCH.value();
    }

    @Override
    public DeliveryResult handle(DeliveryContext context) {
        if (manifestService == null) {
            log.warn("CerosManifestService is not available — cannot fetch manifest for {}",
                    context.manifestUrl);
            return DeliveryResult.EMPTY;
        }
        // The pasted URL is validated and canonicalised to a trusted,
        // Ceros-owned manifest URL at authoring time (CerosFlexInlinePostProcessor),
        // so render does no resolution or extra network call. fetchPublicManifestFromUrl
        // still enforces the Ceros-owned whitelist as a defence-in-depth gate.
        String url = normaliseManifestUrl(context.manifestUrl);
        try {
            CerosManifestV1 manifest = manifestService.fetchPublicManifestFromUrl(url);
            ResolvedPage resolved = resolveDeepLinkedPage(manifest, url, context);
            return toResult(resolved);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to fetch Ceros manifest from {}: {}", url, e.getMessage(), e);
            return DeliveryResult.EMPTY;
        }
    }

    private ResolvedPage resolveDeepLinkedPage(CerosManifestV1 manifest,
                                               String requestedUrl,
                                               DeliveryContext context) {
        String requestedSlug = manifest != null
                ? DeepLinkResolver.requestedSlug(context.request, manifest.getExperience())
                : null;
        if (requestedSlug == null || manifest == null) {
            return new ResolvedPage(manifest, requestedUrl);
        }
        for (CerosManifestV1.PageRef page : manifest.getPages()) {
            if (!requestedSlug.equals(page.getSlug())) {
                continue;
            }
            if (page.isCurrent()) {
                return new ResolvedPage(manifest, requestedUrl);
            }
            String pageUrl = page.getManifestUrl();
            if (pageUrl == null || pageUrl.isEmpty()) {
                log.warn("Deep-link page {} for experience {} has no manifestUrl",
                        requestedSlug, manifest.getExperience().getSlug());
                return new ResolvedPage(manifest, requestedUrl);
            }
            try {
                return new ResolvedPage(manifestService.fetchPublicManifestFromUrl(pageUrl), pageUrl);
            } catch (IOException | IllegalArgumentException e) {
                log.error("Failed to fetch manifest for deep-link page {} ({}): {}",
                        requestedSlug, pageUrl, e.getMessage(), e);
                return new ResolvedPage(manifest, requestedUrl);
            }
        }
        log.debug("No page matching deep-link slug {} found in manifest for experience {}",
                requestedSlug, manifest.getExperience().getSlug());
        return new ResolvedPage(manifest, requestedUrl);
    }

    private DeliveryResult toResult(ResolvedPage resolved) {
        DeliveryResult.Builder b = DeliveryResult.builder()
                .manifestUrl(resolved.url)
                .experienceUrl(DeliveryResult.deriveExperienceUrl(resolved.url));
        ManifestRenderer.renderInto(b, resolved.manifest);
        return b.build();
    }

    public static String normaliseManifestUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (DeliveryResult.isManifestJsonUrl(trimmed)) {
            return trimmed;
        }
        return (trimmed.endsWith("/") ? trimmed : trimmed + "/") + CerosConstants.DEFAULT_ASSET_FILE_PATH;
    }

    private static final class ResolvedPage {
        final CerosManifestV1 manifest;
        final String url;

        ResolvedPage(CerosManifestV1 manifest, String url) {
            this.manifest = manifest;
            this.url = url;
        }
    }
}
