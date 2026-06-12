package com.ceros.servlets;

import com.ceros.jobs.CerosFetchManifestJobConsumer;
import com.ceros.services.CerosManifestService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosManifestStoreServletTest {

    @Mock private SlingHttpServletRequest request;
    @Mock private SlingHttpServletResponse response;
    @Mock private CerosManifestService manifestService;
    @Mock private JobManager jobManager;
    @Mock private ResourceResolver resolver;
    @Mock private Job job;

    private CerosManifestStoreServlet servlet;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new CerosManifestStoreServlet();
        setField("cerosManifestService", manifestService);
        setField("jobManager", jobManager);
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        lenient().when(request.getResourceResolver()).thenReturn(resolver);
        lenient().when(jobManager.addJob(anyString(), anyMap())).thenReturn(job);
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
        verify(jobManager, never()).addJob(anyString(), anyMap());
        assertTrue(responseWriter.toString().contains("manifestUrl parameter is required"));
    }

    @Test
    void doPostReturnsBadRequestWhenBlankManifestUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("   ");
        servlet.doPost(request, response);
        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        verify(jobManager, never()).addJob(anyString(), anyMap());
    }

    @Test
    void doPostRejectsUrlWhenServiceValidationFails() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("http://example.com/exp");
        doThrow(new IllegalArgumentException("scheme must be https"))
                .when(manifestService).validateManifestUrl(anyString());

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        verify(jobManager, never()).addJob(anyString(), anyMap());
        assertTrue(responseWriter.toString().contains("scheme must be https"));
    }

    @Test
    void doPostEnqueuesJobWithNormalisedManifestUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp");

        servlet.doPost(request, response);

        ArgumentCaptor<Map<String, Object>> payload = captureJobPayload();
        assertEquals("https://example.com/exp/manifest.v1.json",
                payload.getValue().get(CerosFetchManifestJobConsumer.PROP_MANIFEST_URL));
    }

    @Test
    void doPostLeavesExistingManifestJsonUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");

        servlet.doPost(request, response);

        ArgumentCaptor<Map<String, Object>> payload = captureJobPayload();
        assertEquals("https://example.com/exp/manifest.json",
                payload.getValue().get(CerosFetchManifestJobConsumer.PROP_MANIFEST_URL));
    }

    @Test
    void doPostReturnsAcceptedWithJobIdAndStatusUrl() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_ACCEPTED);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\":\"accepted\""));
        assertTrue(body.contains("\"jobId\""));
        assertTrue(body.contains("/bin/ceros/fetch-manifest-status.json?jobId="));
    }

    @Test
    void doPostForwardsComponentPathToJob() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath"))
                .thenReturn("/content/mysite/jcr:content/root/cerosflex");

        servlet.doPost(request, response);

        ArgumentCaptor<Map<String, Object>> payload = captureJobPayload();
        assertEquals("/content/mysite/jcr:content/root/cerosflex",
                payload.getValue().get(CerosFetchManifestJobConsumer.PROP_COMPONENT_PATH));
    }

    @Test
    void doPostReplacesEncodedJcrContentInComponentPath() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath"))
                .thenReturn("/content/mysite/_jcr_content/root/cerosflex");

        servlet.doPost(request, response);

        ArgumentCaptor<Map<String, Object>> payload = captureJobPayload();
        assertEquals("/content/mysite/jcr:content/root/cerosflex",
                payload.getValue().get(CerosFetchManifestJobConsumer.PROP_COMPONENT_PATH));
    }

    @Test
    void doPostOmitsComponentPathWhenInvalid() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/*/test");

        servlet.doPost(request, response);

        ArgumentCaptor<Map<String, Object>> payload = captureJobPayload();
        assertFalse(payload.getValue().containsKey(CerosFetchManifestJobConsumer.PROP_COMPONENT_PATH));
    }

    @Test
    void doPostOmitsComponentPathOnTraversal() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(request.getParameter("componentPath")).thenReturn("/content/../etc");

        servlet.doPost(request, response);

        ArgumentCaptor<Map<String, Object>> payload = captureJobPayload();
        assertFalse(payload.getValue().containsKey(CerosFetchManifestJobConsumer.PROP_COMPONENT_PATH));
    }

    @Test
    void doPostReturns500WhenJobManagerReturnsNull() throws Exception {
        when(request.getParameter("manifestUrl")).thenReturn("https://example.com/exp/manifest.json");
        when(jobManager.addJob(anyString(), anyMap())).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertTrue(responseWriter.toString().contains("Could not enqueue fetch job"));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureJobPayload() {
        ArgumentCaptor<Map<String, Object>> captor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(jobManager).addJob(eq(CerosFetchManifestJobConsumer.TOPIC), captor.capture());
        return captor;
    }
}
