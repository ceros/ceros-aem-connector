package com.ceros.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.RequestDispatcher;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosAnalyticsCheckboxServletTest {

    @Mock private SlingHttpServletRequest request;
    @Mock private SlingHttpServletResponse response;
    @Mock private RequestPathInfo pathInfo;
    @Mock private Resource field;
    @Mock private Resource component;
    @Mock private RequestDispatcher dispatcher;

    private final CerosAnalyticsCheckboxServlet servlet = new CerosAnalyticsCheckboxServlet();

    @BeforeEach
    void setUp() {
        Map<String, Object> fieldProps = new HashMap<>();
        fieldProps.put("name", "./cerosAnalytics");
        fieldProps.put("value", "true");
        fieldProps.put("uncheckedValue", "false");
        lenient().when(field.getValueMap()).thenReturn(new ValueMapDecorator(fieldProps));
        lenient().when(request.getResource()).thenReturn(field);
        lenient().when(request.getRequestPathInfo()).thenReturn(pathInfo);
        lenient().when(request.getRequestDispatcher(any(Resource.class))).thenReturn(dispatcher);
    }

    private void stored(String cerosAnalytics, String cerosMode) {
        Map<String, Object> props = new HashMap<>();
        if (cerosAnalytics != null) props.put("cerosAnalytics", cerosAnalytics);
        if (cerosMode != null) props.put("cerosMode", cerosMode);
        when(component.getValueMap()).thenReturn(new ValueMapDecorator(props));
        when(pathInfo.getSuffixResource()).thenReturn(component);
    }

    private Resource render() throws Exception {
        servlet.doGet(request, response);
        ArgumentCaptor<Resource> included = ArgumentCaptor.forClass(Resource.class);
        verify(request).getRequestDispatcher(included.capture());
        verify(dispatcher).include(request, response);
        return included.getValue();
    }

    @Test
    void rendersAGraniteCheckboxWithTheFieldsOwnConfig() throws Exception {
        stored("true", "fetch");
        Resource checkbox = render();
        assertEquals(CerosAnalyticsCheckboxServlet.CHECKBOX_RESOURCE_TYPE, checkbox.getResourceType());
        assertEquals("./cerosAnalytics", checkbox.getValueMap().get("name", String.class));
        assertEquals("false", checkbox.adaptTo(ValueMap.class).get("uncheckedValue", String.class));
    }

    @Test
    void anOldIframeEmbedOpensChecked() throws Exception {
        stored(null, "embed");
        assertTrue(render().getValueMap().get("checked", Boolean.class));
    }

    @Test
    void anOldFetchOrInlineComponentOpensUnchecked() throws Exception {
        stored(null, "inline");
        assertFalse(render().getValueMap().get("checked", Boolean.class));
    }

    @Test
    void aComponentNotSavedYetDefaultsToOn() throws Exception {
        when(pathInfo.getSuffixResource()).thenReturn(null);
        assertTrue(render().getValueMap().get("checked", Boolean.class));
    }

    @Test
    void theDefaultFollowsTheStoredChoice() {
        // Granite ignores the default once a value is stored; it still
        // mirrors what the page renders.
        assertFalse(CerosAnalyticsCheckboxServlet.isCheckedByDefault(componentWith("false", "embed")));
        assertFalse(CerosAnalyticsCheckboxServlet.isCheckedByDefault(componentWith("", "embed")));
        assertTrue(CerosAnalyticsCheckboxServlet.isCheckedByDefault(componentWith("true", "inline")));
    }

    private Resource componentWith(String cerosAnalytics, String cerosMode) {
        Resource r = mock(Resource.class);
        Map<String, Object> props = new HashMap<>();
        props.put("cerosAnalytics", cerosAnalytics);
        props.put("cerosMode", cerosMode);
        when(r.getValueMap()).thenReturn(new ValueMapDecorator(props));
        return r;
    }
}
