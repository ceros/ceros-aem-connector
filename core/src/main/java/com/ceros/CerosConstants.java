package com.ceros;

public final class CerosConstants {
    public static final String DEFAULT_ASSET_FILE_PATH = "manifest.v1.json";

    /**
     * Response header a published Flex experience page advertises, carrying the
     * canonical, Ceros-owned manifest URL. Read from the experience page when a
     * pasted URL is on a (possibly attacker-influenced) vanity domain so the
     * connector can discover the real manifest URL without trusting that host.
     *
     * <p>Set by Flex Shield on standalone HTML page responses; see
     * {@code ceros-spark} PR #9861.</p>
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

    /** User-facing message when an experience URL can't be reached to verify it. */
    public static final String MSG_UNREACHABLE_EXPERIENCE =
            "Could not reach the experience to verify it. Please check the URL and try again in a moment.";

    private CerosConstants() {}
}
