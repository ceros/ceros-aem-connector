package com.ceros.servlets;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.CerosManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosManifestStoreServletTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private SlingHttpServletRequest request;
    @Mock private SlingHttpServletResponse response;
    @Mock private CerosManifestService manifestService;
    @Mock private CerosAssetStorageService assetStorageService;
    @Mock private ResourceResolver resolver;

    private CerosManifestStoreServlet servlet;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new CerosManifestStoreServlet();
        setField("cerosManifestService", manifestService);
        setField("cerosAssetStorageService", assetStorageService);
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        lenient().when(request.getResourceResolver()).thenReturn(resolver);
        lenient().when(assetStorageService.uploadAssets(any(), any())).thenReturn(Map.of());
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosManifestStoreServlet.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(servlet, value);
    }

    @Test
    void doPostReturnsBadRequestWhenNoManifestUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn(null);
        servlet.doPost(request, response);
        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        assertTrue(responseWriter.toString().contains("manifestUrl parameter is required"));
    }

    @Test
    void doPostReturnsBadRequestWhenBlankManifestUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("   ");
        servlet.doPost(request, response);
        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void doPostReturnsBadGatewayOnFetchFailure() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp");
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenThrow(new IOException("connection refused"));

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_GATEWAY);
        assertTrue(responseWriter.toString().contains("connection refused"));
    }

    @Test
    void doPostAppendsManifestJsonToUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp");

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.json")).thenReturn(manifest);

        servlet.doPost(request, response);

        verify(manifestService).fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.json");
        assertTrue(responseWriter.toString().contains("\"status\":\"ok\""));
    }

    @Test
    void doPostDoesNotAppendManifestJsonIfAlreadyPresent() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.com/exp/manifest.json")).thenReturn(manifest);

        servlet.doPost(request, response);

        verify(manifestService).fetchPublicManifestFromUrl("https://example.com/exp/manifest.json");
    }

    @Test
    void doPostAppendsSlashBeforeManifestJson() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp");

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.json")).thenReturn(manifest);

        servlet.doPost(request, response);
        verify(manifestService).fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.json");
    }

    @Test
    void doPostUploadsAssets() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");

        CerosManifestV0 manifest = MAPPER.readValue(
                "{\"deliveryModes\":{\"ssr\":{\"styles\":[{\"url\":\"https://cdn/style.css\"}]}}}",
                CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        Map<String, String> urlMap = new LinkedHashMap<>();
        urlMap.put("https://cdn/style.css", "/content/dam/ceros/exp/style.css");
        when(assetStorageService.uploadAssets(any(), any())).thenReturn(urlMap);

        servlet.doPost(request, response);

        verify(assetStorageService).uploadAssets(any(), eq(resolver));
        assertTrue(responseWriter.toString().contains("\"status\":\"ok\""));
    }

    @Test
    void doPostCallsStoreManifestWhenComponentPathProvided() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/mysite/jcr:content/root/cerosflex");
        when(manifestService.storeManifest(any(), anyString(), anyString(), any(CerosManifestV0.class), any()))
                .thenReturn(true);

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        servlet.doPost(request, response);

        verify(manifestService).storeManifest(eq(resolver),
                eq("/content/mysite/jcr:content/root/cerosflex"),
                eq("https://example.com/exp/manifest.json"),
                any(CerosManifestV0.class), any());
        assertTrue(responseWriter.toString().contains("\"saved\":true"));
    }

    @Test
    void doPostReplacesJcrContentInComponentPath() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/mysite/_jcr_content/root/cerosflex");
        when(manifestService.storeManifest(any(), anyString(), anyString(), any(CerosManifestV0.class), any()))
                .thenReturn(true);

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        servlet.doPost(request, response);

        verify(manifestService).storeManifest(any(),
                eq("/content/mysite/jcr:content/root/cerosflex"),
                anyString(), any(CerosManifestV0.class), any());
    }

    @Test
    void doPostSkipsSaveWhenComponentPathContainsWildcard() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/*/test");

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        servlet.doPost(request, response);

        verify(manifestService, never()).storeManifest(any(), anyString(), anyString(), any(CerosManifestV0.class), any());
        assertTrue(responseWriter.toString().contains("\"saved\":false"));
    }

    @Test
    void doPostSkipsSaveWhenNoComponentPath() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn(null);

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        servlet.doPost(request, response);

        assertTrue(responseWriter.toString().contains("\"saved\":false"));
    }

    @Test
    void doPostPassesUrlMapToStoreManifest() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/mysite/jcr:content/root/cerosflex");
        when(manifestService.storeManifest(any(), anyString(), anyString(), any(CerosManifestV0.class), any()))
                .thenReturn(true);

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);

        Map<String, String> urlMap = new LinkedHashMap<>();
        urlMap.put("https://cdn/a.css", "/dam/a.css");
        urlMap.put("https://cdn/b.js", "/dam/b.js");
        when(assetStorageService.uploadAssets(any(), any())).thenReturn(urlMap);

        servlet.doPost(request, response);

        verify(manifestService).storeManifest(any(), anyString(), anyString(),
                any(CerosManifestV0.class), eq(urlMap));
    }

    @Test
    void doPostReturnsErrorOnAssetUploadFailure() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl(anyString())).thenReturn(manifest);
        when(assetStorageService.uploadAssets(any(), any()))
                .thenThrow(new RuntimeException("DAM error"));

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertTrue(responseWriter.toString().contains("Failed to upload assets to DAM"));
    }

    @Test
    void doPostPreservesV01ManifestUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.v0.1.json");

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.1.json")).thenReturn(manifest);

        servlet.doPost(request, response);

        verify(manifestService).fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.1.json");
        assertTrue(responseWriter.toString().contains("\"status\":\"ok\""));
    }

    @Test
    void doPostDoesNotAppendManifestJsonToV01Url() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.v0.1.json");

        CerosManifestV0 manifest = MAPPER.readValue("{}", CerosManifestV0.class);
        when(manifestService.fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.1.json")).thenReturn(manifest);

        servlet.doPost(request, response);
        verify(manifestService).fetchPublicManifestFromUrl("https://example.com/exp/manifest.v0.1.json");
    }
}
