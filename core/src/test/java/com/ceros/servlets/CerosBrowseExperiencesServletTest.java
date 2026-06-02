package com.ceros.servlets;

import com.ceros.services.CerosAuthenticatedApiService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosBrowseExperiencesServletTest {

    @Mock private SlingHttpServletRequest request;
    @Mock private SlingHttpServletResponse response;
    @Mock private CerosAuthenticatedApiService flexApiService;
    @Mock private RequestPathInfo requestPathInfo;

    private CerosBrowseExperiencesServlet servlet;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new CerosBrowseExperiencesServlet();
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        lenient().when(request.getRequestPathInfo()).thenReturn(requestPathInfo);
    }

    private void setFlexApiService(CerosAuthenticatedApiService svc) throws Exception {
        Field f = CerosBrowseExperiencesServlet.class.getDeclaredField("flexApiService");
        f.setAccessible(true);
        f.set(servlet, svc);
    }

    @Test
    void configEndpointReturnsEnabledTrueWhenServiceEnabled() throws Exception {
        setFlexApiService(flexApiService);
        when(flexApiService.isEnabled()).thenReturn(true);
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/config");

        servlet.doGet(request, response);

        assertEquals("{\"enabled\":true}", responseWriter.toString());
        verify(response).setContentType("application/json");
        verify(response).setHeader("Cache-Control", "private, no-store");
    }

    @Test
    void configEndpointReturnsEnabledFalseWhenServiceDisabled() throws Exception {
        setFlexApiService(flexApiService);
        when(flexApiService.isEnabled()).thenReturn(false);
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/config");

        servlet.doGet(request, response);

        assertEquals("{\"enabled\":false}", responseWriter.toString());
    }

    @Test
    void configEndpointReturnsEnabledFalseWhenNotConfigured() throws Exception {
        setFlexApiService(flexApiService);
        when(flexApiService.isEnabled()).thenReturn(false);
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/config");

        servlet.doGet(request, response);

        assertEquals("{\"enabled\":false}", responseWriter.toString());
    }

    @Test
    void folderTreeEndpointReturnsServiceUnavailableWhenDisabled() throws Exception {
        setFlexApiService(flexApiService);
        when(flexApiService.isEnabled()).thenReturn(false);
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/folder-tree");

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertTrue(responseWriter.toString().contains("not configured"));
    }

    @Test
    void folderTreeEndpointReturnsServiceUnavailableWhenNotEnabled() throws Exception {
        setFlexApiService(flexApiService);
        when(flexApiService.isEnabled()).thenReturn(false);
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/folder-tree");

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    void folderTreeEndpointReturnsJson() throws Exception {
        setFlexApiService(flexApiService);
        when(flexApiService.isEnabled()).thenReturn(true);
        when(flexApiService.getFolderTreeJson()).thenReturn("{\"folders\":[]}");
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/folder-tree");

        servlet.doGet(request, response);

        assertEquals("{\"folders\":[]}", responseWriter.toString());
    }

    @Test
    void folderTreeEndpointReturnsBadGatewayOnError() throws Exception {
        setFlexApiService(flexApiService);
        when(flexApiService.isEnabled()).thenReturn(true);
        when(flexApiService.getFolderTreeJson()).thenThrow(new IOException("API timeout"));
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/folder-tree");

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_GATEWAY);
        assertTrue(responseWriter.toString().contains("API timeout"));
    }

    @Test
    void unknownEndpointReturnsBadRequest() throws Exception {
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/unknown");

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        assertTrue(responseWriter.toString().contains("Unknown endpoint"));
    }

    @Test
    void setsResponseHeaders() throws Exception {
        setFlexApiService(flexApiService);
        when(requestPathInfo.getResourcePath()).thenReturn("/bin/ceros/flex-api/config");

        servlet.doGet(request, response);

        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        verify(response).setHeader("Cache-Control", "private, no-store");
    }
}
