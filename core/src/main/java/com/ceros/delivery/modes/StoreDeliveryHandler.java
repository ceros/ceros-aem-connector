package com.ceros.delivery.modes;

import com.ceros.delivery.DeepLinkResolver;
import com.ceros.delivery.DeliveryResult;
import com.ceros.delivery.ManifestRenderer;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.ceros.services.CerosAssetStorageService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Pre-fetched mode: reads a {@link StoredManifestBundle} from JCR and serves
 * the page that matches the deep-link query param. Fully offline — never
 * makes a network call at render time.
 */
public final class StoreDeliveryHandler implements DeliveryHandler {

    private static final Logger log = LoggerFactory.getLogger(StoreDeliveryHandler.class);

    private final CerosAssetStorageService assetStorageService;

    public StoreDeliveryHandler(CerosAssetStorageService assetStorageService) {
        this.assetStorageService = assetStorageService;
    }

    @Override
    public String mode() {
        return CerosDeliveryMode.STORE.value();
    }

    @Override
    public DeliveryResult handle(DeliveryContext context) {
        if (StringUtils.isBlank(context.prefetchedManifestJson)) {
            return DeliveryResult.EMPTY;
        }
        StoredManifestBundle bundle;
        try {
            bundle = StoredManifestBundle.parse(context.prefetchedManifestJson);
        } catch (IOException e) {
            log.error("Failed to deserialize stored Ceros manifest bundle for {}: {}",
                    context.manifestUrl, e.getMessage(), e);
            return DeliveryResult.EMPTY;
        }
        if (bundle.isEmpty()) {
            return DeliveryResult.EMPTY;
        }

        CerosManifestV1 primary = bundle.manifestFor(bundle.getPrimarySlug());
        String requestedSlug = primary != null
                ? DeepLinkResolver.requestedSlug(context.request, primary.getExperience())
                : null;
        CerosManifestV1 served = bundle.manifestFor(requestedSlug);
        if (served == null) {
            return DeliveryResult.EMPTY;
        }

        // The bundle on the component keeps original CDN URLs in pages[] — those
        // drive `experienceUrl`, which the iframe preview in author mode loads
        // from the public Ceros host. `manifestUrl` is a DAM path so the
        // in-browser SPA router fetches the stored manifest locally and chains
        // through pages[] (themselves rewritten to DAM paths in the DAM copy).
        String cdnManifestUrl = manifestUrlFor(primary, served, context.manifestUrl);
        String damManifestUrl = damManifestUrlFor(served, cdnManifestUrl);
        DeliveryResult.Builder b = DeliveryResult.builder()
                .manifestUrl(damManifestUrl)
                .experienceUrl(DeliveryResult.deriveExperienceUrl(cdnManifestUrl));
        ManifestRenderer.renderInto(b, served);
        return b.build();
    }

    /**
     * For the primary page, keeps the URL the author originally entered (preserved
     * on the component). For deep-linked pages, looks up the page-specific URL
     * from {@code primary.pages[]} so {@code data-flex-manifest-url} on the
     * client matches the page that was rendered.
     */
    private static String manifestUrlFor(CerosManifestV1 primary, CerosManifestV1 served, String fallback) {
        if (primary == served || primary == null || served == null) {
            return fallback;
        }
        String servedSlug = served.getExperience() != null ? served.getExperience().getPageSlug() : null;
        if (servedSlug == null) {
            return fallback;
        }
        for (CerosManifestV1.PageRef page : primary.getPages()) {
            if (servedSlug.equals(page.getSlug()) && StringUtils.isNotBlank(page.getManifestUrl())) {
                return page.getManifestUrl();
            }
        }
        return fallback;
    }

    private String damManifestUrlFor(CerosManifestV1 served, String fallback) {
        if (assetStorageService == null || served.getExperience() == null) {
            return fallback;
        }
        String expSlug = served.getExperience().getSlug();
        String pageSlug = served.getExperience().getPageSlug();
        if (StringUtils.isBlank(expSlug) || StringUtils.isBlank(pageSlug)) {
            return fallback;
        }
        return assetStorageService.damPathForManifest(expSlug, pageSlug);
    }
}
