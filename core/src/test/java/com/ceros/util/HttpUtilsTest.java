package com.ceros.util;

import com.ceros.CerosConstants;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUtilsTest {

    private static final List<String> CEROS_DOMAINS =
            List.of("ceros.site", "example.com");

    @Test
    void requireSafeFetchUrl_acceptsHttps() {
        assertDoesNotThrow(() ->
                HttpUtils.requireSafeFetchUrl("https://cdn.ceros.site/exp/manifest.v1.json",
                        false, false));
    }

    @Test
    void requireSafeFetchUrl_rejectsHttpByDefault() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("http://cdn.ceros.site/exp/manifest.v1.json",
                        false, false));
    }

    @Test
    void requireSafeFetchUrl_acceptsHttpWhenAllowed() {
        assertDoesNotThrow(() ->
                HttpUtils.requireSafeFetchUrl("http://cdn.ceros.site/exp/manifest.v1.json",
                        true, false));
    }

    @Test
    void requireSafeFetchUrl_rejectsIpv4Literal() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("https://169.254.169.254/latest/meta-data/",
                        false, false));
    }

    @Test
    void requireSafeFetchUrl_rejectsIpv6Literal() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("https://[::1]/manifest.v1.json",
                        false, false));
    }

    @Test
    void requireSafeFetchUrl_rejectsLocalhost() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("https://localhost/manifest.v1.json",
                        false, false));
    }

    @Test
    void requireSafeFetchUrl_rejectsLocalhostAlias() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("https://ceros-qa.localhost/manifest.v1.json",
                        false, false));
    }

    @Test
    void requireSafeFetchUrl_acceptsLocalAddressesWhenAllowed() {
        assertDoesNotThrow(() ->
                HttpUtils.requireSafeFetchUrl("http://ceros-qa.localhost:8900/exp/manifest.v1.json",
                        true, true));
        assertDoesNotThrow(() ->
                HttpUtils.requireSafeFetchUrl("http://127.0.0.1:8900/exp/manifest.v1.json",
                        true, true));
    }

    @Test
    void requireSafeFetchUrl_rejectsFileScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("file:///etc/passwd",
                        true, true));
    }

    @Test
    void requireSafeFetchUrl_rejectsJavascriptScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("javascript:alert(1)",
                        true, true));
    }

    @Test
    void requireSafeFetchUrl_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl(null, true, true));
    }

    @Test
    void requireSafeFetchUrl_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("   ", true, true));
    }

    @Test
    void requireSafeFetchUrl_rejectsSchemeOnly() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("https://", false, false));
    }

    @Test
    void requireSafeFetchUrl_rejectsMissingScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.requireSafeFetchUrl("//cdn.ceros.site/exp/manifest.v1.json",
                        false, false));
    }

    // ---- isUrlInAllowedDomains ----

    @Test
    void isUrlInAllowedDomains_acceptsExactApex() {
        assertTrue(HttpUtils.isUrlInAllowedDomains("https://ceros.site/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_acceptsSubdomain() {
        assertTrue(HttpUtils.isUrlInAllowedDomains(
                "https://acme.ceros.site/exp/manifest.v1.json", CEROS_DOMAINS));
        assertTrue(HttpUtils.isUrlInAllowedDomains(
                "https://acme.ceros.site/exp", CEROS_DOMAINS));
        assertTrue(HttpUtils.isUrlInAllowedDomains(
                "https://a.b.example.com/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsHttp() {
        assertFalse(HttpUtils.isUrlInAllowedDomains("http://ceros.site/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsLookalikePrefix() {
        // Substring match would wrongly accept this; exact-suffix must reject it.
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://evilceros.site/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsLookalikeSuffix() {
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://ceros.site.evil.com/exp", CEROS_DOMAINS));
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://ceros.site.attacker.net/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsUnrelatedHost() {
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://customer.com/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsNullBlankOrEmptyDomains() {
        assertFalse(HttpUtils.isUrlInAllowedDomains(null, CEROS_DOMAINS));
        assertFalse(HttpUtils.isUrlInAllowedDomains("   ", CEROS_DOMAINS));
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://ceros.site/exp", List.of()));
    }

    @Test
    void isUrlInAllowedDomains_rejectsUnparseableUrl() {
        assertFalse(HttpUtils.isUrlInAllowedDomains("ht!tp://no", CEROS_DOMAINS));
    }

    @Test
    void defaultDomains_trustOnlyFlexProductionHost() {
        List<String> defaults = Arrays.asList(CerosConstants.DEFAULT_CEROS_OWNED_DOMAINS);
        // The Flex production host is trusted out of the box.
        assertTrue(HttpUtils.isUrlInAllowedDomains("https://acme.ceros.site/exp/manifest.v1.json", defaults));
        // Nothing else is — not Studio (ceros.com), and no non-production host
        // (those must be added per environment via OSGi config).
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://view.ceros.com/exp", defaults));
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://acme.example.com/exp/manifest.v1.json", defaults));
    }
}
