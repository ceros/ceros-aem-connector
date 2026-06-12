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

    /**
     * Validates that {@code manifestUrl} is acceptable as an outbound fetch
     * target under the current OSGi policy. Lets the enqueue path reject bad
     * URLs synchronously instead of failing later inside a background job.
     *
     * @throws IllegalArgumentException if the URL is malformed or violates policy
     */
    void validateManifestUrl(String manifestUrl);

    /**
     * Runs the full fetch + asset upload + persist pipeline for store mode.
     *
     * <p>Progress is reported through {@code progress} so a background
     * job consumer can update a poll-friendly status record. The method
     * never throws on per-asset failures (those are swallowed by the asset
     * storage layer); it throws only on policy violations or hard fetch
     * errors that should abort the whole run.</p>
     *
     * @param manifestUrl   already normalised manifest URL
     * @param componentPath validated JCR path of the cerosflex component, or
     *                      {@code null} to fetch+upload without persisting
     * @param progress      progress callback (use {@link FetchProgress#NOOP}
     *                      when not needed)
     * @param resolver      resource resolver with read access to the manifest
     *                      source and write access to DAM and the component
     */
    void performFetchAndStore(String manifestUrl, String componentPath,
                              FetchProgress progress, ResourceResolver resolver)
            throws IOException;
}
