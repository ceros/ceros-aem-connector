package com.ceros.delivery.modes;

import com.ceros.delivery.DeliveryResult;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreDeliveryHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private SlingHttpServletRequest request;

    private final StoreDeliveryHandler handler = new StoreDeliveryHandler(null);

    private DeliveryHandler.DeliveryContext ctx(String manifestUrl, String bundleJson) {
        return new DeliveryHandler.DeliveryContext(manifestUrl, bundleJson, request, null);
    }

    private CerosManifestV1 page(String slug, String html) throws Exception {
        return MAPPER.readValue(""
                + "{"
                + "  \"experience\":{\"slug\":\"my-exp\",\"accountSlug\":\"acme\",\"pageSlug\":" + MAPPER.writeValueAsString(slug) + "},"
                + "  \"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":" + MAPPER.writeValueAsString(html) + "}}]"
                + "}", CerosManifestV1.class);
    }

    /**
     * Builds a primary manifest that also exposes the pages[] index — exactly what
     * the servlet would persist for the primary slug entry.
     */
    private CerosManifestV1 primaryWithPages(String html) throws Exception {
        return MAPPER.readValue(""
                + "{"
                + "  \"experience\":{\"slug\":\"my-exp\",\"accountSlug\":\"acme\",\"pageSlug\":\"page-1\"},"
                + "  \"assets\":[{\"type\":\"html-body\",\"src\":{\"type\":\"inline\",\"content\":" + MAPPER.writeValueAsString(html) + "}}],"
                + "  \"pages\":["
                + "    {\"slug\":\"page-1\",\"manifestUrl\":\"https://example.ceros.site/exp/page-1/manifest.json\",\"current\":true},"
                + "    {\"slug\":\"page-2\",\"manifestUrl\":\"https://example.ceros.site/exp/page-2/manifest.json\"}"
                + "  ]"
                + "}", CerosManifestV1.class);
    }

    private String bundleJson(String primarySlug, CerosManifestV1... pages) throws Exception {
        LinkedHashMap<String, CerosManifestV1> map = new LinkedHashMap<>();
        for (CerosManifestV1 p : pages) {
            map.put(p.getExperience().getPageSlug(), p);
        }
        return new StoredManifestBundle(primarySlug, map).toJson();
    }

    @Test
    void modeIsStore() {
        assertEquals("store", handler.mode());
    }

    @Test
    void blankBundleJsonReturnsEmptyResult() {
        DeliveryResult r = handler.handle(ctx("https://x/manifest.json", "   "));
        assertFalse(r.isHasContent());
    }

    @Test
    void corruptBundleJsonReturnsEmptyResult() {
        DeliveryResult r = handler.handle(ctx("https://x/manifest.json", "not-json"));
        assertFalse(r.isHasContent());
    }

    @Test
    void servesPrimaryPageWhenNoDeepLinkParam() throws Exception {
        when(request.getParameter(anyString())).thenReturn(null);
        String json = bundleJson("page-1", primaryWithPages("<p>1</p>"), page("page-2", "<p>2</p>"));

        DeliveryResult r = handler.handle(ctx("https://example.ceros.site/exp/page-1/manifest.json", json));

        assertEquals("<p>1</p>", r.getHtmlContent());
        assertEquals("https://example.ceros.site/exp/page-1/manifest.json", r.getManifestUrl());
    }

    @Test
    void servesRequestedPageFromBundleOffline() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("page-2");
        String json = bundleJson("page-1", primaryWithPages("<p>1</p>"), page("page-2", "<p>2</p>"));

        DeliveryResult r = handler.handle(ctx("https://example.ceros.site/exp/page-1/manifest.json", json));

        assertEquals("<p>2</p>", r.getHtmlContent());
        assertEquals("https://example.ceros.site/exp/page-2/manifest.json", r.getManifestUrl());
        assertEquals("https://example.ceros.site/exp/page-2", r.getExperienceUrl());
    }

    @Test
    void usesAccountSlugCollisionFallback() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn(null);
        when(request.getParameter("cer_acme__my-exp")).thenReturn("page-2");
        String json = bundleJson("page-1", primaryWithPages("<p>1</p>"), page("page-2", "<p>2</p>"));

        DeliveryResult r = handler.handle(ctx("https://example.ceros.site/exp/page-1/manifest.json", json));

        assertEquals("<p>2</p>", r.getHtmlContent());
    }

    @Test
    void unknownDeepLinkSlugFallsBackToPrimary() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("page-nope");
        String json = bundleJson("page-1", primaryWithPages("<p>1</p>"), page("page-2", "<p>2</p>"));

        DeliveryResult r = handler.handle(ctx("https://example.ceros.site/exp/page-1/manifest.json", json));

        assertEquals("<p>1</p>", r.getHtmlContent());
    }

}
