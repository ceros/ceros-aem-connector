package com.ceros.models;

import com.ceros.services.CerosFlexFeatureConfig;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CerosFlexModelTest {

    @Mock private Resource resource;

    private CerosFlexModel model;

    @BeforeEach
    void setUp() {
        model = new CerosFlexModel();
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosFlexModel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(model, value);
    }

    @Test
    void blankManifestUrlIsNotConfigured() throws Exception {
        setField("manifestUrl", "  ");
        assertFalse(model.isConfigured());
    }

    @Test
    void nullManifestUrlIsNotConfigured() {
        assertFalse(model.isConfigured());
    }

    @Test
    void populatedManifestUrlIsConfigured() throws Exception {
        setField("manifestUrl", "https://example.com/manifest.json");
        assertTrue(model.isConfigured());
    }

    @Test
    void manifestUrlGetterTrims() throws Exception {
        setField("manifestUrl", "  https://example.com/manifest.json  ");
        assertEquals("https://example.com/manifest.json", model.getManifestUrl());
    }

    @Test
    void storeModeFlag() throws Exception {
        setField("cerosMode", "store");
        assertTrue(model.isStoreMode());
        assertFalse(model.isEmbedMode());
    }

    @Test
    void importModeIsConfiguredWhenBundlePresentEvenWithoutManifestUrl() throws Exception {
        // Import has no manifest URL (the dialog clears it on save); the imported
        // bundle is what makes it configured.
        setField("cerosMode", "import");
        setField("cerosPrefetchedManifestJson", "{\"primarySlug\":\"page-1\"}");
        assertTrue(model.isConfigured());
    }

    @Test
    void importModeNotConfiguredWithoutBundle() throws Exception {
        setField("cerosMode", "import");
        assertFalse(model.isConfigured());
    }

    @Test
    void importModeFlag() throws Exception {
        setField("cerosMode", "import");
        assertTrue(model.isImportMode());
        assertFalse(model.isStoreMode());
        assertFalse(model.isEmbedMode());
        assertFalse(model.isInlineMode());
    }

    @Test
    void embedModeFlag() throws Exception {
        setField("cerosMode", "embed");
        assertTrue(model.isEmbedMode());
        assertFalse(model.isStoreMode());
    }

    @Test
    void inlineModeFlag() throws Exception {
        setField("cerosMode", "inline");
        assertTrue(model.isInlineMode());
        assertFalse(model.isEmbedMode());
        assertFalse(model.isStoreMode());
    }

    @Test
    void getPagePreviewUrlStripsJcrContentSuffix() throws Exception {
        setField("resource", resource);
        when(resource.getPath()).thenReturn("/content/site/page/jcr:content/root/cerosflex");

        assertEquals("/content/site/page.html?wcmmode=disabled", model.getPagePreviewUrl());
    }

    @Test
    void getPagePreviewUrlReturnsNullForOrphanResource() {
        assertNull(model.getPagePreviewUrl());
    }

    @Test
    void modeConstantsMatchHandlerKeys() {
        assertEquals("fetch", CerosFlexModel.MODE_FETCH);
        assertEquals("store", CerosFlexModel.MODE_STORE);
        assertEquals("import", CerosFlexModel.MODE_IMPORT);
        assertEquals("embed", CerosFlexModel.MODE_EMBED);
        assertEquals("inline", CerosFlexModel.MODE_INLINE);
    }

    // --- inline height (data-flex-height) ---

    private CerosFlexFeatureConfig enabledFlag(boolean enabled) throws Exception {
        CerosFlexFeatureConfig flag = org.mockito.Mockito.mock(CerosFlexFeatureConfig.class);
        org.mockito.Mockito.lenient().when(flag.isInlineHeightControlEnabled()).thenReturn(enabled);
        setField("featureConfig", flag);
        return flag;
    }

    @Test
    void inlineHeightNullWhenFlagOffEvenWithScrollingAuthored() throws Exception {
        enabledFlag(false);
        setField("cerosInlineType", "scrolling");
        setField("cerosInlineHeight", "600px");
        assertNull(model.getInlineHeightAttribute());
        assertNull(model.getInlinePreviewStyle());
    }

    @Test
    void inlineHeightNullForFullHeightWithFlagOn() throws Exception {
        enabledFlag(true);
        setField("cerosInlineType", "fullheight");
        assertNull(model.getInlineHeightAttribute());
        assertNull(model.getInlinePreviewStyle());
    }

    @Test
    void inlineHeightEmittedForScrollingWithFlagOn() throws Exception {
        enabledFlag(true);
        setField("cerosInlineType", "scrolling");
        setField("cerosInlineHeight", "600px");
        assertEquals("600px", model.getInlineHeightAttribute());
        assertEquals("height:600px;overflow:auto;", model.getInlinePreviewStyle());
    }

    @Test
    void inlineHeightDefaultsWhenScrollingWithBlankHeight() throws Exception {
        enabledFlag(true);
        setField("cerosInlineType", "scrolling");
        setField("cerosInlineHeight", "  ");
        assertEquals("800px", model.getInlineHeightAttribute());
        assertEquals("height:800px;overflow:auto;", model.getInlinePreviewStyle());
    }

    @Test
    void inlineHeightNullWhenFeatureConfigUnset() throws Exception {
        // Direct-construction safety: no OSGi injection means both getters null.
        setField("cerosInlineType", "scrolling");
        assertNull(model.getInlineHeightAttribute());
        assertNull(model.getInlinePreviewStyle());
    }

    @Test
    void legacyInlineComponentEmitsNoAttributeWithFlagOn() throws Exception {
        // Backward compat: only cerosMode=inline set, no cerosInlineType or
        // cerosInlineHeight at all — rendered markup matches today's output
        // byte-for-byte (attribute omitted).
        enabledFlag(true);
        setField("cerosMode", "inline");
        assertNull(model.getInlineHeightAttribute());
        assertNull(model.getInlinePreviewStyle());
    }

    @Test
    void embedHeightAttributeAutoForFullHeightAndConfiguredForScrolling() throws Exception {
        assertEquals("auto", model.getEmbedHeightAttribute());
        setField("cerosEmbedType", "scrolling");
        setField("cerosEmbedHeight", "  ");
        assertEquals("800px", model.getEmbedHeightAttribute());
        setField("cerosEmbedHeight", "650px");
        assertEquals("650px", model.getEmbedHeightAttribute());
    }
}
