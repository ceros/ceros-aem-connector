package com.ceros.services;

import com.ceros.models.cerosflex.CerosManifestV1;
import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.util.Map;
// (Map<String, byte[]> archive entries are produced by com.ceros.util.ArchiveUtils)

/**
 * Downloads assets referenced by a Ceros manifest and uploads them to AEM DAM.
 *
 * <p>After upload, the manifest's internal URLs are rewritten to point at the
 * DAM copies so the published page has no runtime CDN dependency.</p>
 */
public interface CerosAssetStorageService {

    /**
     * Downloads all CSS, JS, font, and media assets referenced by the manifest
     * and stores them under the configured DAM base path.
     *
     * @param manifest the parsed Ceros manifest whose asset URLs will be rewritten in-place
     * @param resolver a live resource resolver used to obtain the Granite {@code AssetManager}
     * @return a map of original CDN URLs to their new DAM paths
     * @throws IOException if a critical download or storage operation fails
     */
    Map<String, String> uploadAssets(CerosManifestV1 manifest, ResourceResolver resolver) throws IOException;

    /**
     * Archive-sourced counterpart of {@link #uploadAssets}: instead of
     * downloading each referenced asset over HTTP, resolves its bytes from an
     * already-extracted Ceros export archive ({@code relative path -> bytes},
     * as produced by {@code com.ceros.util.ArchiveUtils}). Used by HTML-import
     * mode. The manifest's asset URLs are rewritten in-place to the DAM copies,
     * mirroring the archive's relative layout under the per-page base path.
     *
     * @param manifest the parsed manifest whose asset URLs will be rewritten in-place
     * @param archive  archive-root-relative path to file bytes
     * @param resolver a live resource resolver used to obtain the Granite {@code AssetManager}
     * @return a map of original (relative) manifest URLs to their new DAM paths
     * @throws IOException if a critical storage operation fails
     */
    Map<String, String> uploadAssetsFromArchive(CerosManifestV1 manifest,
                                                Map<String, byte[]> archive,
                                                ResourceResolver resolver) throws IOException;

    /**
     * Writes {@code manifest} as a JSON DAM asset under the standard per-page
     * path ({@code <damBasePath>/<experienceSlug>/<pageSlug>/manifest.json}) so
     * the in-browser SPA router can fetch it directly via AEM's built-in DAM
     * delivery — no custom servlet, no auth, anonymous-safe on publish.
     *
     * @return the DAM path where the manifest was written, or {@code null} if
     *         the manifest lacks the slugs needed to build the path
     */
    String uploadManifest(CerosManifestV1 manifest, ResourceResolver resolver) throws IOException;

    /**
     * Computes the DAM path where {@link #uploadManifest} would write the
     * manifest for the given slugs. Exposed so render-time code can build
     * sibling-page URLs without re-deriving the convention.
     */
    String damPathForManifest(String experienceSlug, String pageSlug);
}
