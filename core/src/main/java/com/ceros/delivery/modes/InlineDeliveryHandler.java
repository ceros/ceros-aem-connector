package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import org.apache.commons.lang3.StringUtils;

/**
 * Client-side inline mode: emits the Ceros inline marker
 * ({@code <div data-flex-inline data-flex-manifest-url=…>}) plus the
 * {@code flex-client.js} runtime, which fetches the manifest in the browser and
 * renders the experience into a Shadow Root. No SSR markup and no iframe.
 *
 * <p>The runtime URL is not resolved here. It is grabbed from the manifest's
 * inline delivery mode and persisted on the component at authoring time (see
 * {@code CerosFlexInlinePostProcessor}); this handler simply reads that stored
 * value. So render makes no network call and applies no host heuristics.
 */
public final class InlineDeliveryHandler implements DeliveryHandler {

    @Override
    public String mode() {
        return CerosDeliveryMode.INLINE.value();
    }

    @Override
    public DeliveryResult handle(DeliveryContext context) {
        String scriptUrl = StringUtils.trimToNull(context.inlineScriptUrl);
        if (StringUtils.isBlank(context.manifestUrl) || scriptUrl == null) {
            // URL not set, or the inline runtime hasn't been grabbed yet
            // (manifest unreachable / no inline delivery mode at save time).
            return DeliveryResult.EMPTY;
        }
        String manifestUrl = FetchDeliveryHandler.normaliseManifestUrl(context.manifestUrl);
        String experienceUrl = DeliveryResult.deriveExperienceUrl(manifestUrl);
        return DeliveryResult.builder()
                .manifestUrl(manifestUrl)
                .experienceUrl(experienceUrl)
                .inlineScriptUrl(scriptUrl)
                .embedTitle(titleFrom(experienceUrl))
                .hasContent(true)
                .build();
    }

    private static String titleFrom(String experienceUrl) {
        if (experienceUrl == null) {
            return "";
        }
        int lastSlash = experienceUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < experienceUrl.length() - 1) {
            return experienceUrl.substring(lastSlash + 1).replace('-', ' ');
        }
        return "";
    }
}
