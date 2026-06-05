package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;

/**
 * Renders the lightweight iframe-embed mode for the Ceros Flex component.
 * Produces no manifest data — the embed script loads everything client-side.
 */
public final class EmbedDeliveryHandler implements DeliveryHandler {

    public static final String MODE = "embed";

    @Override
    public String mode() {
        return MODE;
    }

    @Override
    public DeliveryResult handle(DeliveryContext context) {
        String experienceUrl = DeliveryResult.deriveExperienceUrl(context.manifestUrl);

        String title = "";
        if (experienceUrl != null) {
            int lastSlash = experienceUrl.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < experienceUrl.length() - 1) {
                title = experienceUrl.substring(lastSlash + 1).replace('-', ' ');
            }
        }

        return DeliveryResult.builder()
                .manifestUrl(context.manifestUrl)
                .experienceUrl(experienceUrl)
                .embedTitle(title)
                .hasContent(true)
                .build();
    }
}
