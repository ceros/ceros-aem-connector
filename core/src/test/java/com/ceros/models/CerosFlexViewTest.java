package com.ceros.models;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.services.CerosFlexDeliveryService;
import com.ceros.services.CerosManifestService;
import com.ceros.services.impl.CerosFlexDeliveryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the {@link CerosFlexView} Sling Model. Exercises the
 * end-to-end glue between {@link CerosFlexModel}, {@link CerosFlexDeliveryService},
 * and the per-mode delivery handlers.
 */
@ExtendWith(MockitoExtension.class)
class CerosFlexViewTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private CerosManifestService manifestService;
    @Mock private SlingHttpServletRequest request;
    @Mock private Resource resource;

    private CerosFlexModel model;
    private CerosFlexView view;

    @BeforeEach
    void setUp() {
        model = new CerosFlexModel();
        view = new CerosFlexView();
    }

    private void setModelField(String name, Object value) throws Exception {
        Field f = CerosFlexModel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(model, value);
    }

    private void setViewField(String name, Object value) throws Exception {
        Field f = CerosFlexView.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(view, value);
    }

    private void initView() throws Exception {
        CerosFlexDeliveryServiceImpl service = new CerosFlexDeliveryServiceImpl();
        Field svcField = CerosFlexDeliveryServiceImpl.class.getDeclaredField("manifestService");
        svcField.setAccessible(true);
        svcField.set(service, manifestService);
        setViewField("model", model);
        setViewField("request", request);
        setViewField("resource", resource);
        setViewField("deliveryService", (CerosFlexDeliveryService) service);

        Method init = CerosFlexView.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(view);
    }

    @Test
    void blankManifestUrlLeavesViewEmpty() throws Exception {
        setModelField("manifestUrl", "  ");
        initView();
        assertFalse(view.isConfigured());
        assertFalse(view.isHasContent());
    }

    @Test
    void defaultsToFetchModeWhenCerosModeUnset() throws Exception {
        setModelField("manifestUrl", "https://example.ceros.site/exp/manifest.json");
        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":\"<p>x</p>\"}}]}",
                CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        initView();

        assertTrue(view.isHasContent());
        assertEquals("<p>x</p>", view.getHtmlContent());
        assertFalse(view.isStoreMode());
        assertFalse(view.isEmbedMode());
    }

    @Test
    void embedModeProducesEmbedTitleAndExperienceUrl() throws Exception {
        setModelField("manifestUrl", "https://example.ceros.site/my-exp/manifest.v0.json");
        setModelField("cerosMode", "embed");
        initView();

        assertTrue(view.isEmbedMode());
        assertTrue(view.isHasContent());
        assertEquals("https://example.ceros.site/my-exp", view.getExperienceUrl());
        assertEquals("my exp", view.getEmbedTitle());
    }

    @Test
    void storeModeWithEmptyBundleJsonLeavesViewEmpty() throws Exception {
        setModelField("manifestUrl", "https://example.com/manifest.json");
        setModelField("cerosMode", "store");
        initView();
        assertTrue(view.isStoreMode());
        assertFalse(view.isHasContent());
    }

    @Test
    void prefetchedAtDelegatesToModel() throws Exception {
        setModelField("cerosPrefetchedAt", "2026-06-05T12:00:00Z");
        setViewField("model", model);
        assertEquals("2026-06-05T12:00:00Z", view.getPrefetchedAt());
    }
}
