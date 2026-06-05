package com.ceros.delivery;

import com.ceros.models.cerosflex.CerosManifestV0;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeepLinkResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private SlingHttpServletRequest request;

    private CerosManifestV0.Experience experience(String slug, String accountSlug) throws Exception {
        return MAPPER.readValue(
                "{\"slug\":" + MAPPER.writeValueAsString(slug)
                        + ",\"accountSlug\":" + MAPPER.writeValueAsString(accountSlug) + "}",
                CerosManifestV0.Experience.class);
    }

    @Test
    void usesExperienceSlugParamFirst() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("page-2");

        assertEquals("page-2",
                DeepLinkResolver.requestedSlug(request, experience("my-exp", "acme")));
    }

    @Test
    void fallsBackToAccountSlugCollisionFormWhenPrimaryParamMissing() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn(null);
        when(request.getParameter("cer_acme__my-exp")).thenReturn("page-2");

        assertEquals("page-2",
                DeepLinkResolver.requestedSlug(request, experience("my-exp", "acme")));
    }

    @Test
    void returnsNullWhenNoParamPresent() throws Exception {
        when(request.getParameter(anyString())).thenReturn(null);
        assertNull(DeepLinkResolver.requestedSlug(request, experience("my-exp", "acme")));
    }

    @Test
    void returnsNullWhenParamIsBlank() throws Exception {
        when(request.getParameter("cer_my-exp")).thenReturn("   ");
        assertNull(DeepLinkResolver.requestedSlug(request, experience("my-exp", "acme")));
    }

    @Test
    void returnsNullForNullExperience() {
        assertNull(DeepLinkResolver.requestedSlug(request, null));
    }

    @Test
    void returnsNullForBlankExperienceSlug() throws Exception {
        assertNull(DeepLinkResolver.requestedSlug(request, experience("", "acme")));
    }

    @Test
    void returnsNullForNullRequest() throws Exception {
        assertNull(DeepLinkResolver.requestedSlug(null, experience("my-exp", "acme")));
    }
}
