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
            List.of("ceros.com", "ceros.site", "cerosdev.site", "cerosstage.site");

    @Test
    void validateOutboundUrl_acceptsHttps() {
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("https://cdn.ceros.com/exp/manifest.v1.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_rejectsHttpByDefault() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("http://cdn.ceros.com/exp/manifest.v1.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_acceptsHttpWhenAllowed() {
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("http://cdn.ceros.com/exp/manifest.v1.json",
                        true, false));
    }

    @Test
    void validateOutboundUrl_rejectsIpv4Literal() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("https://169.254.169.254/latest/meta-data/",
                        false, false));
    }

    @Test
    void validateOutboundUrl_rejectsIpv6Literal() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("https://[::1]/manifest.v1.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_rejectsLocalhost() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("https://localhost/manifest.v1.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_rejectsLocalhostAlias() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("https://ceros-qa.localhost/manifest.v1.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_acceptsLocalAddressesWhenAllowed() {
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("http://ceros-qa.localhost:8900/exp/manifest.v1.json",
                        true, true));
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("http://127.0.0.1:8900/exp/manifest.v1.json",
                        true, true));
    }

    @Test
    void validateOutboundUrl_rejectsFileScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("file:///etc/passwd",
                        true, true));
    }

    @Test
    void validateOutboundUrl_rejectsJavascriptScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("javascript:alert(1)",
                        true, true));
    }

    @Test
    void validateOutboundUrl_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl(null, true, true));
    }

    @Test
    void validateOutboundUrl_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("   ", true, true));
    }

    @Test
    void validateOutboundUrl_rejectsSchemeOnly() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("https://", false, false));
    }

    @Test
    void validateOutboundUrl_rejectsMissingScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("//cdn.ceros.com/exp/manifest.v1.json",
                        false, false));
    }

    // ---- isUrlInAllowedDomains ----

    @Test
    void isUrlInAllowedDomains_acceptsExactApex() {
        assertTrue(HttpUtils.isUrlInAllowedDomains("https://ceros.com/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_acceptsSubdomain() {
        assertTrue(HttpUtils.isUrlInAllowedDomains(
                "https://acme.ceros.site/exp/manifest.v1.json", CEROS_DOMAINS));
        assertTrue(HttpUtils.isUrlInAllowedDomains(
                "https://view.ceros.com/exp", CEROS_DOMAINS));
        assertTrue(HttpUtils.isUrlInAllowedDomains(
                "https://a.b.cerosdev.site/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsHttp() {
        assertFalse(HttpUtils.isUrlInAllowedDomains("http://ceros.com/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsLookalikePrefix() {
        // Substring match would wrongly accept this; exact-suffix must reject it.
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://evilceros.com/exp", CEROS_DOMAINS));
    }

    @Test
    void isUrlInAllowedDomains_rejectsLookalikeSuffix() {
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://ceros.com.evil.com/exp", CEROS_DOMAINS));
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
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://ceros.com/exp", List.of()));
    }

    @Test
    void isUrlInAllowedDomains_rejectsUnparseableUrl() {
        assertFalse(HttpUtils.isUrlInAllowedDomains("ht!tp://no", CEROS_DOMAINS));
    }

    @Test
    void defaultDomains_areProductionOnly_excludingDevAndStage() {
        List<String> defaults = Arrays.asList(CerosConstants.DEFAULT_CEROS_OWNED_DOMAINS);
        // Production hosts are trusted out of the box.
        assertTrue(HttpUtils.isUrlInAllowedDomains("https://acme.ceros.site/exp/manifest.v1.json", defaults));
        assertTrue(HttpUtils.isUrlInAllowedDomains("https://view.ceros.com/exp", defaults));
        // Non-production TLDs are NOT trusted by default (add via config for dev).
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://acme.cerosdev.site/exp/manifest.v1.json", defaults));
        assertFalse(HttpUtils.isUrlInAllowedDomains("https://acme.cerosstage.site/exp/manifest.v1.json", defaults));
    }
}
