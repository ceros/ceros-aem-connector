package com.ceros.servlets;

import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.services.CerosManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.servlets.post.Modification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CerosFlexInlinePostProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE_TYPE = "connectors/ceros/components/cerosflex";
    private static final String PATH = "/content/site/page/jcr:content/root/cerosflex";
    private static final String MANIFEST_URL = "https://ceros-qa.latest.cerosdev.site/exp/manifest.v1.json";
    private static final String CLIENT_URL = "https://assets.latest.cerosdev.site/js/flex-client.js";

    @Mock private CerosManifestService manifestService;
    @Mock private SlingHttpServletRequest request;
    @Mock private Resource resource;
    @Mock private ModifiableValueMap props;

    private CerosFlexInlinePostProcessor processor;
    private List<Modification> changes;

    @BeforeEach
    void setUp() throws Exception {
        processor = new CerosFlexInlinePostProcessor();
        Field f = CerosFlexInlinePostProcessor.class.getDeclaredField("cerosManifestService");
        f.setAccessible(true);
        f.set(processor, manifestService);

        changes = new ArrayList<>();
        when(request.getResource()).thenReturn(resource);
        when(resource.isResourceType(RESOURCE_TYPE)).thenReturn(true);
        when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(props);
        when(resource.getPath()).thenReturn(PATH);
    }

    private CerosManifestV1 manifestWithInline() throws IOException {
        return MAPPER.readValue(
                "{\"deliveryModes\":{\"inline\":{\"scripts\":[{\"url\":\"" + CLIENT_URL + "\"}]}}}",
                CerosManifestV1.class);
    }

    @Test
    void inlineModeGrabsAndStoresScriptUrl() throws Exception {
        when(props.get("cerosMode", String.class)).thenReturn("inline");
        when(props.get("manifestUrl", String.class)).thenReturn(MANIFEST_URL);
        when(props.get("cerosInlineScriptUrl", String.class)).thenReturn(null);
        when(manifestService.resolveTrustedManifestUrl(MANIFEST_URL)).thenReturn(MANIFEST_URL);
        when(manifestService.fetchPublicManifestFromUrl(MANIFEST_URL)).thenReturn(manifestWithInline());

        processor.process(request, changes);

        verify(props).put("cerosInlineScriptUrl", CLIENT_URL);
        // Manifest URL already canonical → not rewritten; only the script URL is stored.
        verify(props, never()).put(eq("manifestUrl"), anyString());
        assertEquals(1, changes.size());
    }

    @Test
    void inlineModeRewritesManifestUrlToResolvedCanonical() throws Exception {
        // A vanity experience URL resolves (via x-flex-manifest) to a Ceros host;
        // the canonical URL is persisted so the browser fetches from Ceros.
        String vanityUrl = "https://look.customer.com/exp";
        when(props.get("cerosMode", String.class)).thenReturn("inline");
        when(props.get("manifestUrl", String.class)).thenReturn(vanityUrl);
        when(props.get("cerosInlineScriptUrl", String.class)).thenReturn(null);
        when(manifestService.resolveTrustedManifestUrl(vanityUrl)).thenReturn(MANIFEST_URL);
        when(manifestService.fetchPublicManifestFromUrl(MANIFEST_URL)).thenReturn(manifestWithInline());

        processor.process(request, changes);

        verify(props).put("manifestUrl", MANIFEST_URL);
        verify(props).put("cerosInlineScriptUrl", CLIENT_URL);
        assertEquals(2, changes.size());
    }

    @Test
    void inlineModeUntrustedUrlAbortsSave() throws Exception {
        when(props.get("cerosMode", String.class)).thenReturn("inline");
        when(props.get("manifestUrl", String.class)).thenReturn("https://customer.com/exp");
        when(manifestService.resolveTrustedManifestUrl("https://customer.com/exp"))
                .thenThrow(new IllegalArgumentException("not a recognized Ceros domain"));

        // Throwing aborts the Sling POST, so the dialog refuses to save.
        assertThrows(IllegalArgumentException.class, () -> processor.process(request, changes));

        verify(manifestService, never()).fetchPublicManifestFromUrl(anyString());
        verify(props, never()).put(eq("manifestUrl"), anyString());
    }

    @Test
    void fetchModeCanonicalisesManifestUrlWithoutGrabbingScript() throws Exception {
        // Fetch (live) mode: the pasted URL is validated/canonicalised at save
        // so render trusts it, but no inline script is grabbed.
        String vanityUrl = "https://look.customer.com/exp";
        when(props.get("cerosMode", String.class)).thenReturn("fetch");
        when(props.get("manifestUrl", String.class)).thenReturn(vanityUrl);
        when(props.get("cerosInlineScriptUrl", String.class)).thenReturn(null);
        when(manifestService.resolveTrustedManifestUrl(vanityUrl)).thenReturn(MANIFEST_URL);

        processor.process(request, changes);

        verify(props).put("manifestUrl", MANIFEST_URL);
        verify(manifestService, never()).fetchPublicManifestFromUrl(anyString());
        verify(props, never()).put(eq("cerosInlineScriptUrl"), anyString());
        assertEquals(1, changes.size());
    }

    @Test
    void nonCerosflexResourceIsIgnored() {
        when(resource.isResourceType(RESOURCE_TYPE)).thenReturn(false);

        processor.process(request, changes);

        verifyNoInteractions(manifestService);
        assertTrue(changes.isEmpty());
    }

    @Test
    void nonInlineModeClearsStaleScriptUrl() {
        when(props.get("cerosMode", String.class)).thenReturn("embed");
        when(props.get("cerosInlineScriptUrl", String.class)).thenReturn(CLIENT_URL);

        processor.process(request, changes);

        verify(props).remove("cerosInlineScriptUrl");
        verifyNoInteractions(manifestService);
        assertEquals(1, changes.size());
    }

    @Test
    void inlineModeUnreachableAbortsSave() throws Exception {
        when(props.get("cerosMode", String.class)).thenReturn("inline");
        when(props.get("manifestUrl", String.class)).thenReturn(MANIFEST_URL);
        when(manifestService.resolveTrustedManifestUrl(MANIFEST_URL)).thenReturn(MANIFEST_URL);
        when(manifestService.fetchPublicManifestFromUrl(MANIFEST_URL))
                .thenThrow(new IOException("connection refused"));

        assertThrows(IllegalArgumentException.class, () -> processor.process(request, changes));
    }

    @Test
    void inlineModeNoInlineDeliveryModeAbortsSave() throws Exception {
        // Manifest resolves and is reachable, but exposes no inline delivery mode.
        when(props.get("cerosMode", String.class)).thenReturn("inline");
        when(props.get("manifestUrl", String.class)).thenReturn(MANIFEST_URL);
        when(manifestService.resolveTrustedManifestUrl(MANIFEST_URL)).thenReturn(MANIFEST_URL);
        when(manifestService.fetchPublicManifestFromUrl(MANIFEST_URL))
                .thenReturn(MAPPER.readValue("{\"deliveryModes\":{}}", CerosManifestV1.class));

        assertThrows(IllegalArgumentException.class, () -> processor.process(request, changes));
        verify(props, never()).put(eq("cerosInlineScriptUrl"), anyString());
    }

    @Test
    void embedModeUntrustedUrlAbortsSave() throws Exception {
        // Iframe (embed) mode is now validated on save too.
        when(props.get("cerosMode", String.class)).thenReturn("embed");
        when(props.get("manifestUrl", String.class)).thenReturn("https://customer.com/exp");
        when(manifestService.resolveTrustedManifestUrl("https://customer.com/exp"))
                .thenThrow(new IllegalArgumentException("not a recognized Ceros domain"));

        assertThrows(IllegalArgumentException.class, () -> processor.process(request, changes));
    }

    @Test
    void embedModeValidUrlSavesWithoutRewritingOrGrabbing() throws Exception {
        // Embed validates the URL but keeps the pasted (possibly vanity) URL —
        // the experience is loaded in a client-side iframe, not fetched.
        String vanityUrl = "https://look.customer.com/exp";
        when(props.get("cerosMode", String.class)).thenReturn("embed");
        when(props.get("manifestUrl", String.class)).thenReturn(vanityUrl);
        when(props.get("cerosInlineScriptUrl", String.class)).thenReturn(null);
        when(manifestService.resolveTrustedManifestUrl(vanityUrl)).thenReturn(MANIFEST_URL);

        processor.process(request, changes);

        verify(manifestService).resolveTrustedManifestUrl(vanityUrl);
        verify(props, never()).put(eq("manifestUrl"), anyString());
        verify(manifestService, never()).fetchPublicManifestFromUrl(anyString());
        assertTrue(changes.isEmpty());
    }

    @Test
    void storeModeUntrustedUrlAbortsSave() throws Exception {
        when(props.get("cerosMode", String.class)).thenReturn("store");
        when(props.get("manifestUrl", String.class)).thenReturn("https://customer.com/exp");
        when(manifestService.resolveTrustedManifestUrl("https://customer.com/exp"))
                .thenThrow(new IllegalArgumentException("not a recognized Ceros domain"));

        assertThrows(IllegalArgumentException.class, () -> processor.process(request, changes));
    }

    @Test
    void unchangedScriptUrlWritesNothing() throws Exception {
        when(props.get("cerosMode", String.class)).thenReturn("inline");
        when(props.get("manifestUrl", String.class)).thenReturn(MANIFEST_URL);
        when(props.get("cerosInlineScriptUrl", String.class)).thenReturn(CLIENT_URL);
        when(manifestService.resolveTrustedManifestUrl(MANIFEST_URL)).thenReturn(MANIFEST_URL);
        when(manifestService.fetchPublicManifestFromUrl(MANIFEST_URL)).thenReturn(manifestWithInline());

        processor.process(request, changes);

        verify(props, never()).put(eq("cerosInlineScriptUrl"), anyString());
        verify(props, never()).remove("cerosInlineScriptUrl");
        assertTrue(changes.isEmpty());
    }
}
