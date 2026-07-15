package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InlineDeliveryHandlerTest {

    private static final String EXP_URL = "https://ceros-qa.latest.ceros.site/floating-iron-throughout";
    private static final String MANIFEST_URL = EXP_URL + "/manifest.v1.json";
    private static final String CLIENT_URL = "https://assets.latest.ceros.site/js/flex-client.js";

    private InlineDeliveryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InlineDeliveryHandler();
    }

    /** Inline mode reads the saved script URL from context (grabbed at save time). */
    private DeliveryHandler.DeliveryContext ctx(String manifestUrl, String inlineScriptUrl) {
        return new DeliveryHandler.DeliveryContext(manifestUrl, null, inlineScriptUrl, null, null);
    }

    @Test
    void modeIsInline() {
        assertEquals("inline", handler.mode());
    }

    @Test
    void emitsMarkerUrlAndStoredScript() {
        DeliveryResult r = handler.handle(ctx(MANIFEST_URL, CLIENT_URL));

        assertTrue(r.isHasContent());
        assertEquals(MANIFEST_URL, r.getManifestUrl());
        assertEquals(CLIENT_URL, r.getInlineScriptUrl());
        assertEquals(EXP_URL, r.getExperienceUrl());
    }

    @Test
    void normalisesBareExperienceUrlToManifestJson() {
        DeliveryResult r = handler.handle(ctx(EXP_URL, CLIENT_URL));

        assertEquals(MANIFEST_URL, r.getManifestUrl());
        assertEquals(CLIENT_URL, r.getInlineScriptUrl());
    }

    @Test
    void missingStoredScriptReturnsEmpty() {
        DeliveryResult r = handler.handle(ctx(MANIFEST_URL, null));
        assertFalse(r.isHasContent());
        assertNull(r.getInlineScriptUrl());
    }

    @Test
    void blankManifestUrlReturnsEmpty() {
        DeliveryResult r = handler.handle(ctx("   ", CLIENT_URL));
        assertFalse(r.isHasContent());
    }
}
