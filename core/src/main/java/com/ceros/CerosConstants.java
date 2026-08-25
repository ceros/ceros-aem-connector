package com.ceros;

public final class CerosConstants {
    public static final String DEFAULT_ASSET_FILE_PATH = "manifest.v1.json";

    /**
     * Response header a published Flex experience page advertises, carrying the
     * canonical, Ceros-owned manifest URL. Read from the experience page when a
     * pasted URL is on a (possibly attacker-influenced) vanity domain so the
     * connector can discover the real manifest URL without trusting that host.
     *
     * <p>Set by Ceros on standalone HTML page responses.</p>
     */
    public static final String FLEX_MANIFEST_HEADER = "x-flex-manifest";

    /**
     * Default set of Ceros-owned domains trusted to serve Flex manifests and the
     * scripts they reference. A manifest URL is only fetched and injected when
     * its host exactly equals — or is a dotted subdomain of — one of these.
     *
     * <p>Production only. Non-production Ceros domains are deliberately excluded
     * so customer-facing installs never reference internal environments; add them
     * per environment via the {@code cerosOwnedDomains} OSGi config for dev.</p>
     */
    public static final String[] DEFAULT_CEROS_OWNED_DOMAINS = {
        "ceros.site"
    };

    /** Request header carrying the Flex API version on every authenticated call. */
    public static final String FLEX_API_VERSION_HEADER = "x-ceros-api-version";

    /**
     * Flex API version sent when none is configured. Also the shipped default of
     * the {@code flexApiVersion} OSGi property; a blank override falls back to
     * this so the header is never sent empty or omitted.
     */
    public static final String DEFAULT_FLEX_API_VERSION = "2026-08-06-09-00";

    /** User-facing message when an experience URL can't be reached to verify it. */
    public static final String MSG_UNREACHABLE_EXPERIENCE =
            "Could not reach the experience to verify it. Please check the URL and try again in a moment.";

    private CerosConstants() {}
}
