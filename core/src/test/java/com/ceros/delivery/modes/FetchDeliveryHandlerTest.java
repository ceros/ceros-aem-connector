package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.services.CerosManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FetchDeliveryHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PRIMARY_URL = "https://example.ceros.site/exp/page-1/manifest.json";
    private static final String PAGE_2_URL = "https://example.ceros.site/exp/page-2/manifest.json";

    @Mock private CerosManifestService manifestService;
    @Mock private SlingHttpServletRequest request;

    private FetchDeliveryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FetchDeliveryHandler(manifestService);
    }

    private DeliveryHandler.DeliveryContext ctx(String url) {
        return new DeliveryHandler.DeliveryContext(url, null, request, null);
    }

    private CerosManifestV0 manifestWithPages(String html, boolean firstIsCurrent) throws IOException {
        return MAPPER.readValue(""
                + "{"
                + "  \"experience\":{\"slug\":\"my-exp\",\"accountSlug\":\"acme\",\"pageSlug\":\"page-1\"},"
                + "  \"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":" + MAPPER.writeValueAsString(html) + "}}],"
                + "  \"pages\":["
                + "    {\"slug\":\"page-1\",\"manifestUrl\":\"" + PRIMARY_URL + "\",\"isFirst\":true,\"current\":" + firstIsCurrent + "},"
                + "    {\"slug\":\"page-2\",\"manifestUrl\":\"" + PAGE_2_URL + "\",\"current\":" + (!firstIsCurrent) + "}"
                + "  ]"
                + "}", CerosManifestV0.class);
    }

    private CerosManifestV0 simpleManifest(String html) throws IOException {
        return MAPPER.readValue(
                "{\"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":" + MAPPER.writeValueAsString(html) + "}}]}",
                CerosManifestV0.class);
    }

    @Test
    void modeIsFetch() {
        assertEquals("fetch", handler.mode());
    }

    @Test
    void servesOriginalManifestWhenNoDeepLinkParam() throws Exception {
        when(request.getParameter(anyString())).thenReturn(null);
        when(manifestService.fetchPublicManifestFromUrl(PRIMARY_URL)).thenReturn(manifestWithPages("<p>1</p>", true));

        DeliveryResult r = handler.handle(ctx(PRIMARY_URL));

        assertEquals("<p>1</p>", r.getHtmlContent());
        assertEquals(PRIMARY_URL, r.getManifestUrl());
        verify(manifestService, times(1)).fetchPublicManifestFromUrl(anyString());
    }

    @Test
    void swapsToRequestedPageManifest() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("page-2");
        when(manifestService.fetchPublicManifestFromUrl(PRIMARY_URL)).thenReturn(manifestWithPages("<p>1</p>", true));
        when(manifestService.fetchPublicManifestFromUrl(PAGE_2_URL)).thenReturn(simpleManifest("<p>2</p>"));

        DeliveryResult r = handler.handle(ctx(PRIMARY_URL));

        assertEquals("<p>2</p>", r.getHtmlContent());
        assertEquals(PAGE_2_URL, r.getManifestUrl());
        assertEquals("https://example.ceros.site/exp/page-2", r.getExperienceUrl());
    }

    @Test
    void skipsSwapWhenRequestedPageIsCurrent() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("page-1");
        when(manifestService.fetchPublicManifestFromUrl(PRIMARY_URL)).thenReturn(manifestWithPages("<p>1</p>", true));

        DeliveryResult r = handler.handle(ctx(PRIMARY_URL));

        assertEquals("<p>1</p>", r.getHtmlContent());
        verify(manifestService, times(1)).fetchPublicManifestFromUrl(anyString());
    }

    @Test
    void unknownSlugFallsBackToOriginalManifest() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("page-nope");
        when(manifestService.fetchPublicManifestFromUrl(PRIMARY_URL)).thenReturn(manifestWithPages("<p>1</p>", true));

        DeliveryResult r = handler.handle(ctx(PRIMARY_URL));

        assertEquals("<p>1</p>", r.getHtmlContent());
        verify(manifestService, times(1)).fetchPublicManifestFromUrl(anyString());
    }

    @Test
    void usesAccountSlugCollisionFallback() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn(null);
        when(request.getParameter("cer_acme__my-exp")).thenReturn("page-2");
        when(manifestService.fetchPublicManifestFromUrl(PRIMARY_URL)).thenReturn(manifestWithPages("<p>1</p>", true));
        when(manifestService.fetchPublicManifestFromUrl(PAGE_2_URL)).thenReturn(simpleManifest("<p>2</p>"));

        DeliveryResult r = handler.handle(ctx(PRIMARY_URL));

        assertEquals("<p>2</p>", r.getHtmlContent());
    }

    @Test
    void deepLinkFetchFailureFallsBackToPrimary() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("page-2");
        when(manifestService.fetchPublicManifestFromUrl(PRIMARY_URL)).thenReturn(manifestWithPages("<p>1</p>", true));
        when(manifestService.fetchPublicManifestFromUrl(PAGE_2_URL)).thenThrow(new IOException("boom"));

        DeliveryResult r = handler.handle(ctx(PRIMARY_URL));

        assertEquals("<p>1</p>", r.getHtmlContent());
        assertEquals(PRIMARY_URL, r.getManifestUrl());
    }

    @Test
    void primaryFetchFailureReturnsEmptyResult() throws Exception {
        when(manifestService.fetchPublicManifestFromUrl(PRIMARY_URL)).thenThrow(new IOException("connection refused"));

        DeliveryResult r = handler.handle(ctx(PRIMARY_URL));

        assertFalse(r.isHasContent());
        assertNull(r.getHtmlContent());
    }

    @Test
    void missingServiceReturnsEmpty() {
        FetchDeliveryHandler h = new FetchDeliveryHandler(null);
        DeliveryResult r = h.handle(ctx(PRIMARY_URL));
        assertFalse(r.isHasContent());
    }

    @Test
    void normaliseAppendsManifestJsonWhenAbsent() {
        assertEquals("https://example.com/exp/manifest.v0.json",
                FetchDeliveryHandler.normaliseManifestUrl("https://example.com/exp"));
        assertEquals("https://example.com/exp/manifest.v0.json",
                FetchDeliveryHandler.normaliseManifestUrl("https://example.com/exp/"));
    }

    @Test
    void normaliseLeavesExistingManifestJsonUrl() {
        assertEquals("https://example.com/exp/manifest.json",
                FetchDeliveryHandler.normaliseManifestUrl("https://example.com/exp/manifest.json"));
        assertEquals("https://example.com/exp/manifest.v0.1.json",
                FetchDeliveryHandler.normaliseManifestUrl("https://example.com/exp/manifest.v0.1.json"));
    }
}
