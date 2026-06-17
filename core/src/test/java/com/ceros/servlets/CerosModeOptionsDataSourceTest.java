package com.ceros.servlets;

import com.adobe.granite.ui.components.ds.DataSource;
import com.ceros.config.CerosFlexModesConfig;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.caconfig.ConfigurationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosModeOptionsDataSourceTest {

    private static final String COMPONENT_PATH = "/content/site/jcr:content/par/cerosflex";

    @Mock private SlingHttpServletRequest request;
    @Mock private SlingHttpServletResponse response;
    @Mock private RequestPathInfo requestPathInfo;
    @Mock private ResourceResolver resolver;
    @Mock private Resource resource;
    @Mock private ConfigurationBuilder configBuilder;
    @Mock private CerosFlexModesConfig config;

    private CerosModeOptionsDataSource servlet;

    @BeforeEach
    void setUp() {
        servlet = new CerosModeOptionsDataSource();
        lenient().when(request.getResourceResolver()).thenReturn(resolver);
        lenient().when(request.getRequestPathInfo()).thenReturn(requestPathInfo);
    }

    private List<String> runAndCollectValues() {
        servlet.doGet(request, response);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq(DataSource.class.getName()), captor.capture());
        DataSource ds = (DataSource) captor.getValue();
        List<String> values = new ArrayList<>();
        for (Iterator<Resource> it = ds.iterator(); it.hasNext(); ) {
            values.add(it.next().getValueMap().get("value", String.class));
        }
        return values;
    }

    private void stubConfig() {
        when(requestPathInfo.getSuffix()).thenReturn(COMPONENT_PATH);
        when(resolver.getResource(COMPONENT_PATH)).thenReturn(resource);
        when(resource.adaptTo(ConfigurationBuilder.class)).thenReturn(configBuilder);
        when(configBuilder.as(CerosFlexModesConfig.class)).thenReturn(config);
    }

    @Test
    void allModesWhenConfigAllEnabled() {
        stubConfig();
        when(config.fetch()).thenReturn(true);
        when(config.store()).thenReturn(true);
        when(config.htmlImport()).thenReturn(true);
        when(config.inline()).thenReturn(true);
        when(config.embed()).thenReturn(true);

        assertEquals(List.of("fetch", "store", "import", "inline", "embed"), runAndCollectValues());
    }

    @Test
    void disabledModesAreOmitted() {
        stubConfig();
        when(config.fetch()).thenReturn(true);
        when(config.store()).thenReturn(true);
        when(config.htmlImport()).thenReturn(false);
        when(config.inline()).thenReturn(true);
        when(config.embed()).thenReturn(false);

        assertEquals(List.of("fetch", "store", "inline"), runAndCollectValues());
    }

    @Test
    void fallsBackToAllModesWhenNoContext() {
        when(requestPathInfo.getSuffix()).thenReturn(null);

        assertEquals(List.of("fetch", "store", "import", "inline", "embed"), runAndCollectValues());
        verify(resolver, never()).getResource(anyString());
    }

    @Test
    void walksUpToNearestExistingAncestorForNewComponent() {
        when(requestPathInfo.getSuffix()).thenReturn(COMPONENT_PATH);
        when(resolver.getResource(COMPONENT_PATH)).thenReturn(null);
        when(resolver.getResource("/content/site/jcr:content/par")).thenReturn(resource);
        when(resource.adaptTo(ConfigurationBuilder.class)).thenReturn(configBuilder);
        when(configBuilder.as(CerosFlexModesConfig.class)).thenReturn(config);
        when(config.fetch()).thenReturn(false);
        when(config.store()).thenReturn(true);
        when(config.htmlImport()).thenReturn(true);
        when(config.inline()).thenReturn(true);
        when(config.embed()).thenReturn(true);

        assertEquals(List.of("store", "import", "inline", "embed"), runAndCollectValues());
    }
}
