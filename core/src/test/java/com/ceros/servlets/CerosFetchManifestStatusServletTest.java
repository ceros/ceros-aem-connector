package com.ceros.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosFetchManifestStatusServletTest {

    @Mock private SlingHttpServletRequest request;
    @Mock private SlingHttpServletResponse response;
    @Mock private ResourceResolver resolver;
    @Mock private Resource resource;

    private CerosFetchManifestStatusServlet servlet;
    private StringWriter responseWriter;

    private static final String VALID_JOB_ID = "abcdef01-1234-5678-9abc-def012345678";

    @BeforeEach
    void setUp() throws Exception {
        servlet = new CerosFetchManifestStatusServlet();
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        lenient().when(request.getResourceResolver()).thenReturn(resolver);
    }

    @Test
    void doGetReturnsBadRequestWhenJobIdMissing() throws Exception {
        when(request.getParameter("jobId")).thenReturn(null);
        servlet.doGet(request, response);
        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void doGetReturnsBadRequestWhenJobIdHasIllegalChars() throws Exception {
        when(request.getParameter("jobId")).thenReturn("../etc/passwd");
        servlet.doGet(request, response);
        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        verify(resolver, never()).getResource(anyString());
    }

    @Test
    void doGetReturnsNotFoundWhenJobMissing() throws Exception {
        when(request.getParameter("jobId")).thenReturn(VALID_JOB_ID);
        when(resolver.getResource("/var/ceros/fetch-jobs/" + VALID_JOB_ID)).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
        assertTrue(responseWriter.toString().contains("Unknown jobId"));
    }

    @Test
    void doGetReturnsStatusForRunningJob() throws Exception {
        when(request.getParameter("jobId")).thenReturn(VALID_JOB_ID);
        when(resolver.getResource("/var/ceros/fetch-jobs/" + VALID_JOB_ID)).thenReturn(resource);
        Map<String, Object> props = new HashMap<>();
        props.put("status", "running");
        props.put("phase", "uploading-assets");
        props.put("pagesProcessed", 3L);
        props.put("pagesTotal", 7L);
        props.put("manifestUrl", "https://example.com/exp/manifest.v0.json");
        ValueMap valueMap = new ValueMapDecorator(props);
        when(resource.getValueMap()).thenReturn(valueMap);

        servlet.doGet(request, response);

        String body = responseWriter.toString();
        assertTrue(body.contains("\"jobId\":\"" + VALID_JOB_ID + "\""));
        assertTrue(body.contains("\"status\":\"running\""));
        assertTrue(body.contains("\"phase\":\"uploading-assets\""));
        assertTrue(body.contains("\"pagesProcessed\":3"));
        assertTrue(body.contains("\"pagesTotal\":7"));
    }

    @Test
    void doGetReturnsErrorForFailedJob() throws Exception {
        when(request.getParameter("jobId")).thenReturn(VALID_JOB_ID);
        when(resolver.getResource("/var/ceros/fetch-jobs/" + VALID_JOB_ID)).thenReturn(resource);
        Map<String, Object> props = new HashMap<>();
        props.put("status", "failed");
        props.put("error", "connection refused");
        when(resource.getValueMap()).thenReturn(new ValueMapDecorator(props));

        servlet.doGet(request, response);

        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\":\"failed\""));
        assertTrue(body.contains("\"error\":\"connection refused\""));
    }

    @Test
    void doGetFlipsStalePendingToFailed() throws Exception {
        when(request.getParameter("jobId")).thenReturn(VALID_JOB_ID);
        when(resolver.getResource("/var/ceros/fetch-jobs/" + VALID_JOB_ID)).thenReturn(resource);
        Map<String, Object> props = new HashMap<>();
        props.put("status", "pending");
        // No "phase" property — consumer never reported progress.
        props.put("startedAt", Instant.now().minusSeconds(120).toString());
        when(resource.getValueMap()).thenReturn(new ValueMapDecorator(props));

        servlet.doGet(request, response);

        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\":\"failed\""));
        assertTrue(body.contains("Fetch job did not start"));
    }

    @Test
    void doGetKeepsRecentPendingAsPending() throws Exception {
        when(request.getParameter("jobId")).thenReturn(VALID_JOB_ID);
        when(resolver.getResource("/var/ceros/fetch-jobs/" + VALID_JOB_ID)).thenReturn(resource);
        Map<String, Object> props = new HashMap<>();
        props.put("status", "pending");
        props.put("startedAt", Instant.now().minusSeconds(5).toString());
        when(resource.getValueMap()).thenReturn(new ValueMapDecorator(props));

        servlet.doGet(request, response);

        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\":\"pending\""));
        assertFalse(body.contains("\"status\":\"failed\""));
    }

    @Test
    void doGetKeepsPendingWithPhaseAsPending() throws Exception {
        // A long-running phase isn't stale; phase set means consumer is alive.
        when(request.getParameter("jobId")).thenReturn(VALID_JOB_ID);
        when(resolver.getResource("/var/ceros/fetch-jobs/" + VALID_JOB_ID)).thenReturn(resource);
        Map<String, Object> props = new HashMap<>();
        props.put("status", "running");
        props.put("phase", "uploading-assets");
        props.put("startedAt", Instant.now().minusSeconds(600).toString());
        when(resource.getValueMap()).thenReturn(new ValueMapDecorator(props));

        servlet.doGet(request, response);

        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\":\"running\""));
    }

    @Test
    void doGetReturnsSuccessAndFetchedAtForCompletedJob() throws Exception {
        when(request.getParameter("jobId")).thenReturn(VALID_JOB_ID);
        when(resolver.getResource("/var/ceros/fetch-jobs/" + VALID_JOB_ID)).thenReturn(resource);
        Map<String, Object> props = new HashMap<>();
        props.put("status", "success");
        props.put("fetchedAt", "2026-06-12T10:11:12Z");
        props.put("saved", true);
        when(resource.getValueMap()).thenReturn(new ValueMapDecorator(props));

        servlet.doGet(request, response);

        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\":\"success\""));
        assertTrue(body.contains("\"fetchedAt\":\"2026-06-12T10:11:12Z\""));
        assertTrue(body.contains("\"saved\":true"));
    }
}
