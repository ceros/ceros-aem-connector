package com.ceros.delivery.modes;

import com.ceros.delivery.DeepLinkResolver;
import com.ceros.delivery.DeliveryResult;
import com.ceros.delivery.ManifestRenderer;
import com.ceros.delivery.DeliveryResult.Builder;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.models.cerosflex.StoredManifestBundle;
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

    public static final String MODE = "store";

    private static final Logger log = LoggerFactory.getLogger(StoreDeliveryHandler.class);

    @Override
    public String mode() {
        return MODE;
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

        String url = manifestUrlFor(primary, served, context.manifestUrl);
        DeliveryResult.Builder b = DeliveryResult.builder()
                .manifestUrl(url)
                .experienceUrl(DeliveryResult.deriveExperienceUrl(url));
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
}
