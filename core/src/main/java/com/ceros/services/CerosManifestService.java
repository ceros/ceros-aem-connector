package com.ceros.services;

import com.ceros.models.cerosflex.CerosManifestV1;
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
    CerosManifestV1 fetchPublicManifestFromUrl(String manifestUrl) throws IOException;

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
     * Resolves a user-supplied (pasted) experience or manifest URL into a
     * trusted, Ceros-owned manifest URL — the security gate for the keyless
     * paste flow.
     *
     * <p>We do not trust the pasted host by default. If it is already a
     * Ceros-owned host the manifest URL is constructed directly; otherwise the
     * pasted page is treated as a (possibly attacker-influenced) vanity domain
     * and asked to advertise its canonical manifest URL via the
     * {@code x-flex-manifest} response header. Either way the resolved manifest
     * URL must itself be Ceros-owned before it is returned, so only manifests —
     * and the scripts they reference — served from a Ceros TLD are ever fetched
     * and injected.</p>
     *
     * @param rawUrl the URL pasted by the author
     * @return a validated, Ceros-owned manifest URL safe to fetch
     * @throws IllegalArgumentException if the URL cannot be resolved to a
     *                                  Ceros-owned manifest (untrusted host, no
     *                                  discovery header, policy violation)
     * @throws IOException              if the experience page could not be
     *                                  reached to read its discovery header
     */
    String resolveTrustedManifestUrl(String rawUrl) throws IOException;

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

    /**
     * Runs the full unpack + asset upload + persist pipeline for HTML-import
     * mode. Reads the {@code .tar.gz} export previously uploaded to
     * {@code archivePath} (under {@code /var/ceros/imports}), unpacks it,
     * stores each page's assets and manifest into the DAM, and persists the
     * bundle to the component with {@code cerosMode=import}.
     *
     * <p>The stored end state is identical to {@link #performFetchAndStore}, so
     * the component renders through the same offline store handler.</p>
     *
     * @param archivePath   JCR path of the uploaded archive ({@code nt:file})
     * @param componentPath validated JCR path of the cerosflex component, or
     *                      {@code null} to unpack without persisting
     * @param progress      progress callback (use {@link FetchProgress#NOOP} when not needed)
     * @param resolver      resource resolver with read access to {@code archivePath}
     *                      and write access to DAM and the component
     */
    void performImportAndStore(String archivePath, String componentPath,
                               FetchProgress progress, ResourceResolver resolver)
            throws IOException;
}
