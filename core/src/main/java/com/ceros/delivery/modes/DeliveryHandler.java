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
        switch (CerosDeliveryMode.fromValue(mode)) {
            case EMBED:
                return new EmbedDeliveryHandler();
            case INLINE:
                return new InlineDeliveryHandler();
            case STORE:
            case IMPORT:
                // Import produces the same stored end state as store mode
                // (bundle on the component + assets in DAM), so it renders
                // through the same offline handler.
                return new StoreDeliveryHandler(assetStorageService);
            case FETCH:
            default:
                return new FetchDeliveryHandler(manifestService);
        }
    }

    /**
     * Carries everything a handler needs from the Sling Model into its init
     * logic. Mutable fields are read-only from the handler's perspective.
     */
    final class DeliveryContext {
        public final String manifestUrl;
        public final String prefetchedManifestJson;
        /**
         * The {@code flex-client.js} URL grabbed from the manifest's inline
         * delivery mode and persisted on the component at authoring time. Only
         * set for inline mode; {@code null} otherwise.
         */
        public final String inlineScriptUrl;
        public final SlingHttpServletRequest request;
        public final Resource resource;

        public DeliveryContext(String manifestUrl,
                               String prefetchedManifestJson,
                               SlingHttpServletRequest request,
                               Resource resource) {
            this(manifestUrl, prefetchedManifestJson, null, request, resource);
        }

        public DeliveryContext(String manifestUrl,
                               String prefetchedManifestJson,
                               String inlineScriptUrl,
                               SlingHttpServletRequest request,
                               Resource resource) {
            this.manifestUrl = manifestUrl;
            this.prefetchedManifestJson = prefetchedManifestJson;
            this.inlineScriptUrl = inlineScriptUrl;
            this.request = request;
            this.resource = resource;
        }
    }
}
