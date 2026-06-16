package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class InlineDeliveryHandlerTest {

    private static final String EXP_URL = "https://ceros-qa.latest.cerosdev.site/floating-iron-throughout";
    private static final String MANIFEST_URL = EXP_URL + "/manifest.v1.json";
    private static final String CLIENT_URL = "https://assets.latest.cerosdev.site/js/flex-client.js";

    @Mock private SlingHttpServletRequest request;

    private InlineDeliveryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InlineDeliveryHandler();
    }

    private DeliveryHandler.DeliveryContext ctx(String url) {
        return new DeliveryHandler.DeliveryContext(url, null, request, null);
    }

    @Test
    void modeIsInline() {
        assertEquals("inline", handler.mode());
    }

    @Test
    void emitsMarkerUrlAndDerivedClientScript() {
        DeliveryResult r = handler.handle(ctx(MANIFEST_URL));

        assertTrue(r.isHasContent());
        // data-flex-manifest-url is the public manifest URL the browser fetches.
        assertEquals(MANIFEST_URL, r.getManifestUrl());
        // flex-client.js lives on the assets. sibling of the experience host.
        assertEquals(CLIENT_URL, r.getInlineScriptUrl());
        assertEquals(EXP_URL, r.getExperienceUrl());
    }

    @Test
    void normalisesBareExperienceUrlToManifestJson() {
        DeliveryResult r = handler.handle(ctx(EXP_URL));

        assertEquals(MANIFEST_URL, r.getManifestUrl());
        assertEquals(CLIENT_URL, r.getInlineScriptUrl());
    }

    @Test
    void derivesAssetsHostForProductionDomain() {
        assertEquals("https://assets.ceros.site/js/flex-client.js",
                InlineDeliveryHandler.deriveClientScriptUrl(
                        "https://example.ceros.site/exp/manifest.v1.json"));
    }

    @Test
    void derivesAssetsHostPreservingPortForLocalhost() {
        assertEquals("http://assets.localhost:8900/js/flex-client.js",
                InlineDeliveryHandler.deriveClientScriptUrl(
                        "http://acme.localhost:8900/exp/manifest.v1.json"));
    }

    @Test
    void blankUrlReturnsEmpty() {
        DeliveryResult r = handler.handle(ctx("   "));
        assertFalse(r.isHasContent());
        assertNull(r.getInlineScriptUrl());
    }
}
