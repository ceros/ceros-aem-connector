package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Client-side inline mode: emits the Ceros inline marker
 * ({@code <div data-flex-inline data-flex-manifest-url=…>}) plus the
 * {@code flex-client.js} runtime, which fetches the manifest in the browser and
 * renders the experience into a Shadow Root. No SSR markup and no iframe.
 *
 * <p>Both URLs are derived from the authored experience URL with no network
 * call. The runtime lives on the {@code assets.} sibling of the experience
 * host — the same {@code assets.<flexPlayerHost>} convention the publish
 * pipeline uses when it builds the manifest's inline delivery mode. For the
 * canonical {@code <account>.<host>} experience URL this matches the manifest's
 * own {@code flex-client.js} URL across environments (dev / QA / prod).
 */
public final class InlineDeliveryHandler implements DeliveryHandler {

    public static final String MODE = "inline";

    private static final String FLEX_CLIENT_PATH = "/js/flex-client.js";

    private static final Logger log = LoggerFactory.getLogger(InlineDeliveryHandler.class);

    @Override
    public String mode() {
        return MODE;
    }

    @Override
    public DeliveryResult handle(DeliveryContext context) {
        if (StringUtils.isBlank(context.manifestUrl)) {
            return DeliveryResult.EMPTY;
        }
        String manifestUrl = FetchDeliveryHandler.normaliseManifestUrl(context.manifestUrl);
        String experienceUrl = DeliveryResult.deriveExperienceUrl(manifestUrl);
        String scriptUrl = deriveClientScriptUrl(manifestUrl);
        if (scriptUrl == null) {
            log.warn("Could not derive flex-client.js URL from manifest URL {}", manifestUrl);
            return DeliveryResult.EMPTY;
        }
        return DeliveryResult.builder()
                .manifestUrl(manifestUrl)
                .experienceUrl(experienceUrl)
                .inlineScriptUrl(scriptUrl)
                .embedTitle(titleFrom(experienceUrl))
                .hasContent(true)
                .build();
    }

    /**
     * Builds the {@code flex-client.js} URL by swapping the experience host's
     * leading account label for {@code assets} — e.g.
     * {@code ceros-qa.latest.cerosdev.site} → {@code assets.latest.cerosdev.site}.
     * Mirrors the {@code assets.<flexPlayerHost>} origin the publish pipeline
     * emits for the manifest's inline delivery script. Returns {@code null} if
     * the URL can't be parsed.
     */
    static String deriveClientScriptUrl(String manifestUrl) {
        try {
            URI uri = new URI(manifestUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int dot = host.indexOf('.');
            String baseHost = dot >= 0 ? host.substring(dot + 1) : host;
            String assetsHost = "assets." + baseHost;
            String authority = uri.getPort() != -1 ? assetsHost + ":" + uri.getPort() : assetsHost;
            return scheme + "://" + authority + FLEX_CLIENT_PATH;
        } catch (URISyntaxException e) {
            return null;
        }
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
