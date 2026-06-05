package com.ceros.services;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.models.cerosflex.StoredManifestBundle;
import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.util.Map;

/**
 * Fetches, serialises, and stores Ceros experience manifests.
 */
public interface CerosManifestService {

    /**
     * Fetches a single manifest at the given URL.
     *
     * <p>Used by the live "fetch" delivery mode (render-time call).</p>
     */
    CerosManifestV0 fetchPublicManifestFromUrl(String manifestUrl) throws IOException;

    /**
     * Fetches the manifest at {@code manifestUrl} <em>and</em> every linked
     * page's manifest from its {@code pages[]} array, returning a bundle
     * suitable for full offline rendering in store mode.
     *
     * <p>Per-page fetch failures are logged and skipped — the bundle still
     * contains the primary manifest plus every page that loaded successfully.</p>
     */
    StoredManifestBundle fetchManifestBundle(String manifestUrl) throws IOException;

    /**
     * Persists a manifest bundle to the {@code cerosflex} component at
     * {@code componentPath}. The bundle JSON lands on
     * {@code cerosPrefetchedManifestJson} and a combined asset reference list
     * on {@code cerosAssetReferences}.
     *
     * @return {@code true} if the JCR write succeeded.
     */
    boolean storeManifestBundle(ResourceResolver resolver, String componentPath,
                                String manifestUrl,
                                StoredManifestBundle bundle,
                                Map<String, String> urlMap);
}
