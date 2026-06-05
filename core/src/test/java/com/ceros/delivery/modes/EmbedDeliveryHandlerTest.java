package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbedDeliveryHandlerTest {

    private static DeliveryResult run(String manifestUrl) {
        return new EmbedDeliveryHandler().handle(
                new DeliveryHandler.DeliveryContext(manifestUrl, null, null, null));
    }

    @Test
    void modeIsEmbed() {
        assertEquals("embed", new EmbedDeliveryHandler().mode());
    }

    @Test
    void derivesExperienceUrlAndTitleFromManifestUrl() {
        DeliveryResult r = run("https://example.ceros.site/my-exp/manifest.v0.json");

        assertEquals("https://example.ceros.site/my-exp", r.getExperienceUrl());
        assertEquals("my exp", r.getEmbedTitle());
        assertTrue(r.isHasContent());
        assertNull(r.getHtmlContent());
    }

    @Test
    void titleFallsBackToHostnameForRootUrl() {
        DeliveryResult r = run("https://example.ceros.site/");
        assertEquals("example.ceros.site", r.getEmbedTitle());
    }

    @Test
    void titleHandlesVersionedManifestUrl() {
        DeliveryResult r = run("https://example.ceros.site/my-exp/manifest.v0.1.json");
        assertEquals("my exp", r.getEmbedTitle());
        assertEquals("https://example.ceros.site/my-exp", r.getExperienceUrl());
    }
}
