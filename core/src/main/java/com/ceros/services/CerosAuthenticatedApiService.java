package com.ceros.services;

import java.io.IOException;

/**
 * Authenticated client for the Ceros Flex REST API.
 *
 * <p>Provides a browsable folder-tree of published experiences, enabling AEM
 * authors to pick an experience from a dialog instead of pasting a manifest URL.
 * The service is disabled when no API key is configured.</p>
 */
public interface CerosAuthenticatedApiService {

    /**
     * Returns {@code true} when a Flex API key is configured and the browse
     * feature is available.
     */
    boolean isEnabled();

    /**
     * Fetches the folder tree from the Ceros Flex API, filters to published
     * experiences only, and returns the result as a JSON string suitable for
     * the authoring dialog.
     *
     * @return a JSON string containing account name, folders, and experiences
     * @throws IOException if the API request fails or returns an unexpected response
     */
    String getFolderTreeJson() throws IOException;
}
