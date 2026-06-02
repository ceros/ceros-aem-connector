package com.ceros.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpUtilsTest {

    @Test
    void validateOutboundUrl_acceptsHttps() {
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("https://cdn.ceros.com/exp/manifest.v0.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_rejectsHttpByDefault() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("http://cdn.ceros.com/exp/manifest.v0.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_acceptsHttpWhenAllowed() {
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("http://cdn.ceros.com/exp/manifest.v0.json",
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
                HttpUtils.validateOutboundUrl("https://[::1]/manifest.v0.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_rejectsLocalhost() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("https://localhost/manifest.v0.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_rejectsLocalhostAlias() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUtils.validateOutboundUrl("https://ceros-qa.localhost/manifest.v0.json",
                        false, false));
    }

    @Test
    void validateOutboundUrl_acceptsLocalAddressesWhenAllowed() {
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("http://ceros-qa.localhost:8900/exp/manifest.v0.json",
                        true, true));
        assertDoesNotThrow(() ->
                HttpUtils.validateOutboundUrl("http://127.0.0.1:8900/exp/manifest.v0.json",
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
                HttpUtils.validateOutboundUrl("//cdn.ceros.com/exp/manifest.v0.json",
                        false, false));
    }
}
