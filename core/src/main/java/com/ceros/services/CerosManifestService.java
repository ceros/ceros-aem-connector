package com.ceros.services;

import com.ceros.models.cerosflex.CerosManifestV0;
import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.util.Map;

/**
 * Fetches, serialises, and stores Ceros experience manifests.
 */
public interface CerosManifestService {

    CerosManifestV0 fetchPublicManifestFromUrl(String manifestUrl) throws IOException;

    boolean storeManifest(ResourceResolver resolver, String componentPath,
                          String manifestUrl,
                          CerosManifestV0 manifest,
                          Map<String, String> urlMap);
}
