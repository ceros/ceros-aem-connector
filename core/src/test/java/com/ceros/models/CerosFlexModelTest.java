package com.ceros.models;

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
}
