package com.ceros.delivery;

import com.ceros.models.cerosflex.CerosManifestV0;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;

/**
 * Reads the page-slug requested by the inline SPA router's deep-link URL
 * channel (see ceros-spark #9179).
 *
 * <p>The router writes one of two query params, in priority order:</p>
 * <ol>
 *   <li>{@code cer_<experience.slug>=<pageSlug>} — the normal form.</li>
 *   <li>{@code cer_<accountSlug>__<experience.slug>=<pageSlug>} — the
 *       deterministic fallback used when two embeds with the same
 *       experience slug collide across accounts on a single host page.</li>
 * </ol>
 */
public final class DeepLinkResolver {

    public static final String PARAM_PREFIX = "cer_";

    private DeepLinkResolver() {
        // static utility
    }

    /**
     * Returns the requested page slug for {@code experience} on this request,
     * or {@code null} if none is present.
     */
    public static String requestedSlug(SlingHttpServletRequest request,
                                       CerosManifestV0.Experience experience) {
        if (request == null || experience == null || StringUtils.isBlank(experience.getSlug())) {
            return null;
        }

        String slug = request.getParameter(PARAM_PREFIX + experience.getSlug());
        if (StringUtils.isBlank(slug) && StringUtils.isNotBlank(experience.getAccountSlug())) {
            slug = request.getParameter(
                    PARAM_PREFIX + experience.getAccountSlug() + "__" + experience.getSlug());
        }
        return StringUtils.isBlank(slug) ? null : slug;
    }
}
