package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.CerosManifestService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;

/**
 * Strategy interface for the three Ceros Flex delivery modes. Each mode is
 * given a {@link DeliveryContext} and produces a {@link DeliveryResult} that
 * the Sling Model exposes to HTL.
 */
public interface DeliveryHandler {

    /**
     * Builds a {@link DeliveryResult} from the configured manifest URL and the
     * surrounding request context. Implementations must be side-effect-free
     * apart from logging.
     */
    DeliveryResult handle(DeliveryContext context);

    /** The mode value (e.g. {@code "fetch"}) this handler responds to. */
    String mode();

    /**
     * Picks the handler for {@code mode}. Falls back to the fetch handler for
     * unknown or blank modes so legacy components without an explicit mode keep
     * working.
     */
    static DeliveryHandler forMode(String mode,
                                   CerosManifestService manifestService,
                                   CerosAssetStorageService assetStorageService) {
        if (EmbedDeliveryHandler.MODE.equals(mode)) {
            return new EmbedDeliveryHandler();
        }
        if (StoreDeliveryHandler.MODE.equals(mode)) {
            return new StoreDeliveryHandler(assetStorageService);
        }
        return new FetchDeliveryHandler(manifestService);
    }

    /**
     * Carries everything a handler needs from the Sling Model into its init
     * logic. Mutable fields are read-only from the handler's perspective.
     */
    final class DeliveryContext {
        public final String manifestUrl;
        public final String prefetchedManifestJson;
        public final SlingHttpServletRequest request;
        public final Resource resource;

        public DeliveryContext(String manifestUrl,
                               String prefetchedManifestJson,
                               SlingHttpServletRequest request,
                               Resource resource) {
            this.manifestUrl = manifestUrl;
            this.prefetchedManifestJson = prefetchedManifestJson;
            this.request = request;
            this.resource = resource;
        }
    }
}
