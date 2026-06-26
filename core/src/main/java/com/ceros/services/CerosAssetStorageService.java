package com.ceros.services;

import com.ceros.models.cerosflex.CerosManifestV1;
import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
// (Map<String, byte[]> archive entries are produced by com.ceros.util.ArchiveUtils)

/**
 * Downloads assets referenced by a Ceros manifest and uploads them to AEM DAM.
 *
 * <p>For live "Store" mode the URL-rewriting is owned by flex-shield: the
 * manifest is requested with {@code ?baseUrl=} (see
 * {@link #assetRewriteBaseUrl(String)}) and comes back with its asset URLs
 * already pointing under the DAM base path plus an {@code assetRewrites} map
 * this service {@linkplain #mirrorRewrittenAssets mirrors} into the DAM. For
 * HTML-import mode there is no server, so the archive counterpart still
 * rewrites the manifest URLs in-place.</p>
 */
public interface CerosAssetStorageService {

    /**
     * The absolute {@code baseUrl} to request the flex-shield server-side
     * rewrite against for {@code experienceSlug}. Its path component is the DAM
     * root every asset will be served under; its origin is a sentinel
     * ({@link #assetRewriteOrigin()}) stripped from the response so stored
     * manifests reference assets by root-relative DAM path.
     */
    String assetRewriteBaseUrl(String experienceSlug);

    /**
     * The sentinel origin baked into {@link #assetRewriteBaseUrl(String)}. The
     * Store pipeline strips this prefix from the server-rewritten manifest so
     * the {@code to} URLs collapse to root-relative DAM paths.
     */
    String assetRewriteOrigin();

    /**
     * Mirrors the server's {@code assetRewrites} map: downloads each entry's
     * {@code from} URL and writes it into the DAM at the path the rewrite
     * specifies (under the experience's DAM root). Deduped across pages via
     * {@code seenPaths} so shared bundles download once. The manifest's own
     * asset URLs were already rewritten by the server, so nothing on the
     * manifest is modified here.
     *
     * @param manifest  a server-rewritten manifest carrying {@code assetRewrites}
     * @param resolver  a live resource resolver used to obtain the Granite {@code AssetManager}
     * @param seenPaths relative DAM paths already written this run (mutated)
     * @return a map of original Ceros URL to DAM path for every asset stored
     * @throws IOException if obtaining the {@code AssetManager} or committing fails
     */
    Map<String, String> mirrorRewrittenAssets(CerosManifestV1 manifest,
                                              ResourceResolver resolver,
                                              Set<String> seenPaths) throws IOException;

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
