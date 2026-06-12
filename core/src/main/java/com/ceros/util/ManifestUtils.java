package com.ceros.util;

import com.ceros.models.cerosflex.CerosManifestV1;

public final class ManifestUtils {

    private ManifestUtils() {}

    /**
     * Derives the primary slug from a manifest. Prefers {@code experience.pageSlug},
     * then the {@code current=true} entry in {@code pages[]}.
     */
    public static String primarySlugOf(CerosManifestV1 manifest) {
        if (manifest == null) {
            return null;
        }
        CerosManifestV1.Experience exp = manifest.getExperience();
        if (exp != null && exp.getPageSlug() != null && !exp.getPageSlug().isEmpty()) {
            return exp.getPageSlug();
        }
        for (CerosManifestV1.PageRef page : manifest.getPages()) {
            if (page.isCurrent() && page.getSlug() != null && !page.getSlug().isEmpty()) {
                return page.getSlug();
            }
        }
        return null;
    }
}
