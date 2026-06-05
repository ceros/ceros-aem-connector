package com.ceros.servlets;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.models.cerosflex.StoredManifestBundle;
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

    private static CerosManifestV0 parse(String json) throws IOException {
        return MAPPER.readValue(json, CerosManifestV0.class);
    }

    private static StoredManifestBundle singlePageBundle(String pageSlug, String html) throws IOException {
        LinkedHashMap<String, CerosManifestV0> pages = new LinkedHashMap<>();
        pages.put(pageSlug, parse(""
                + "{\"experience\":{\"slug\":\"e\",\"pageSlug\":" + MAPPER.writeValueAsString(pageSlug) + "},"
                + "\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":" + MAPPER.writeValueAsString(html) + "}}]}"));
        return new StoredManifestBundle(pageSlug, pages);
    }

    // ---- Input validation ----

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
        when(manifestService.fetchManifestBundle(anyString())).thenThrow(new IOException("connection refused"));

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_GATEWAY);
        assertTrue(responseWriter.toString().contains("connection refused"));
    }

    @Test
    void doPostReturnsBadRequestWhenServiceRejectsUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp");
        when(manifestService.fetchManifestBundle(anyString())).thenThrow(new IllegalArgumentException("bad scheme"));

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        assertTrue(responseWriter.toString().contains("bad scheme"));
    }

    // ---- URL normalisation ----

    @Test
    void doPostAppendsManifestJsonWhenAbsent() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp");
        when(manifestService.fetchManifestBundle("https://example.com/exp/manifest.v0.json"))
                .thenReturn(singlePageBundle("home", "<p>1</p>"));

        servlet.doPost(request, response);

        verify(manifestService).fetchManifestBundle("https://example.com/exp/manifest.v0.json");
        assertTrue(responseWriter.toString().contains("\"status\":\"ok\""));
    }

    @Test
    void doPostLeavesExistingManifestJsonUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(manifestService.fetchManifestBundle("https://example.com/exp/manifest.json"))
                .thenReturn(singlePageBundle("home", "<p>1</p>"));

        servlet.doPost(request, response);

        verify(manifestService).fetchManifestBundle("https://example.com/exp/manifest.json");
    }

    @Test
    void doPostPreservesVersionedManifestJsonUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.v0.1.json");
        when(manifestService.fetchManifestBundle("https://example.com/exp/manifest.v0.1.json"))
                .thenReturn(singlePageBundle("home", "<p>1</p>"));

        servlet.doPost(request, response);

        verify(manifestService).fetchManifestBundle("https://example.com/exp/manifest.v0.1.json");
    }

    // ---- Asset upload ----

    @Test
    void doPostUploadsAssetsForEveryPageInBundle() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");

        LinkedHashMap<String, CerosManifestV0> pages = new LinkedHashMap<>();
        pages.put("home", parse("{\"deliveryModes\":{\"ssr\":{\"styles\":[{\"url\":\"https://cdn/home.css\"}]}}}"));
        pages.put("about", parse("{\"deliveryModes\":{\"ssr\":{\"styles\":[{\"url\":\"https://cdn/about.css\"}]}}}"));
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(new StoredManifestBundle("home", pages));

        when(assetStorageService.uploadAssets(any(), any())).thenReturn(Map.of());

        servlet.doPost(request, response);

        verify(assetStorageService, times(2)).uploadAssets(any(), eq(resolver));
        assertTrue(responseWriter.toString().contains("\"pages\":2"));
    }

    @Test
    void doPostReturnsErrorOnAssetUploadFailure() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(singlePageBundle("home", "<p>1</p>"));
        when(assetStorageService.uploadAssets(any(), any())).thenThrow(new RuntimeException("DAM error"));

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertTrue(responseWriter.toString().contains("Failed to upload assets to DAM"));
    }

    // ---- Bundle persistence ----

    @Test
    void doPostPersistsBundleWhenComponentPathProvided() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/mysite/jcr:content/root/cerosflex");
        StoredManifestBundle bundle = singlePageBundle("home", "<p>1</p>");
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(bundle);
        when(manifestService.storeManifestBundle(any(), anyString(), anyString(), eq(bundle), anyMap()))
                .thenReturn(true);

        servlet.doPost(request, response);

        verify(manifestService).storeManifestBundle(eq(resolver),
                eq("/content/mysite/jcr:content/root/cerosflex"),
                eq("https://example.com/exp/manifest.json"),
                eq(bundle), anyMap());
        assertTrue(responseWriter.toString().contains("\"saved\":true"));
    }

    @Test
    void doPostReplacesEncodedJcrContentInComponentPath() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/mysite/_jcr_content/root/cerosflex");
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(singlePageBundle("home", "<p>1</p>"));
        when(manifestService.storeManifestBundle(any(), anyString(), anyString(), any(), anyMap())).thenReturn(true);

        servlet.doPost(request, response);

        verify(manifestService).storeManifestBundle(any(),
                eq("/content/mysite/jcr:content/root/cerosflex"),
                anyString(), any(), anyMap());
    }

    @Test
    void doPostSkipsSaveWhenComponentPathContainsWildcard() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/*/test");
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(singlePageBundle("home", "<p>1</p>"));

        servlet.doPost(request, response);

        verify(manifestService, never()).storeManifestBundle(any(), anyString(), anyString(), any(), anyMap());
        assertTrue(responseWriter.toString().contains("\"saved\":false"));
    }

    @Test
    void doPostSkipsSaveWhenComponentPathTraversesParent() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/../etc");
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(singlePageBundle("home", "<p>1</p>"));

        servlet.doPost(request, response);

        verify(manifestService, never()).storeManifestBundle(any(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void doPostSkipsSaveWhenNoComponentPath() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn(null);
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(singlePageBundle("home", "<p>1</p>"));

        servlet.doPost(request, response);

        verify(manifestService, never()).storeManifestBundle(any(), anyString(), anyString(), any(), anyMap());
        assertTrue(responseWriter.toString().contains("\"saved\":false"));
    }

    @Test
    void doPostForwardsCombinedUrlMapToStore() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/mysite/jcr:content/root/cerosflex");

        LinkedHashMap<String, CerosManifestV0> pages = new LinkedHashMap<>();
        pages.put("home", parse("{}"));
        pages.put("about", parse("{}"));
        when(manifestService.fetchManifestBundle(anyString())).thenReturn(new StoredManifestBundle("home", pages));

        Map<String, String> first = Map.of("https://cdn/a.css", "/dam/a.css");
        Map<String, String> second = Map.of("https://cdn/b.js", "/dam/b.js");
        when(assetStorageService.uploadAssets(any(), any())).thenReturn(first, second);
        when(manifestService.storeManifestBundle(any(), anyString(), anyString(), any(), anyMap())).thenReturn(true);

        servlet.doPost(request, response);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.putAll(first);
        expected.putAll(second);
        verify(manifestService).storeManifestBundle(any(), anyString(), anyString(), any(), eq(expected));
    }
}
