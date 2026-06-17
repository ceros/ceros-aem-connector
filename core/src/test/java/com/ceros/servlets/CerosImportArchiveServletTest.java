package com.ceros.servlets;

import com.ceros.jobs.CerosImportArchiveJobConsumer;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.ValueFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosImportArchiveServletTest {

    private static final String ARCHIVE_NODE_PATH = "/var/ceros/imports/job-x/archive.tar.gz";

    @Mock private SlingHttpServletRequest request;
    @Mock private SlingHttpServletResponse response;
    @Mock private JobManager jobManager;
    @Mock private ResourceResolver resolver;
    @Mock private Session session;
    @Mock private Node node;
    @Mock private ValueFactory valueFactory;
    @Mock private Binary binary;
    @Mock private RequestParameter archive;
    @Mock private Job job;

    private CerosImportArchiveServlet servlet;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new CerosImportArchiveServlet();
        setField("jobManager", jobManager);
        setField("maxArchiveBytes", 52_428_800L);

        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        lenient().when(request.getResourceResolver()).thenReturn(resolver);
        lenient().when(jobManager.addJob(anyString(), anyMap())).thenReturn(job);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosImportArchiveServlet.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(servlet, value);
    }

    /** Wires the JCR + valid-upload mocks for the happy path. */
    private void stubValidUpload() throws Exception {
        when(request.getRequestParameter("archive")).thenReturn(archive);
        when(archive.isFormField()).thenReturn(false);
        when(archive.getSize()).thenReturn(1234L);
        when(archive.getFileName()).thenReturn("experience-123.tar.gz");
        lenient().when(archive.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getRootNode()).thenReturn(node);
        when(node.hasNode(anyString())).thenReturn(true);
        when(node.getNode(anyString())).thenReturn(node);
        when(node.addNode(anyString(), anyString())).thenReturn(node);
        when(node.getPath()).thenReturn(ARCHIVE_NODE_PATH);
        when(session.getValueFactory()).thenReturn(valueFactory);
        when(valueFactory.createBinary(any())).thenReturn(binary);
    }

    @Test
    void returnsBadRequestWhenNoArchive() throws Exception {
        when(request.getRequestParameter("archive")).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        verify(jobManager, never()).addJob(anyString(), anyMap());
    }

    @Test
    void returnsBadRequestWhenArchiveIsEmpty() throws Exception {
        when(request.getRequestParameter("archive")).thenReturn(archive);
        when(archive.isFormField()).thenReturn(false);
        when(archive.getSize()).thenReturn(0L);

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void returnsBadRequestForNonTarGzName() throws Exception {
        when(request.getRequestParameter("archive")).thenReturn(archive);
        when(archive.isFormField()).thenReturn(false);
        when(archive.getSize()).thenReturn(100L);
        when(archive.getFileName()).thenReturn("experience.zip");

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        verify(jobManager, never()).addJob(anyString(), anyMap());
    }

    @Test
    void returnsBadRequestWhenArchiveTooLarge() throws Exception {
        when(request.getRequestParameter("archive")).thenReturn(archive);
        when(archive.isFormField()).thenReturn(false);
        when(archive.getSize()).thenReturn(99_999_999L);
        when(archive.getFileName()).thenReturn("experience.tar.gz");

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void enqueuesJobWithArchivePathOnSuccess() throws Exception {
        stubValidUpload();
        when(request.getParameter("componentPath"))
                .thenReturn("/content/page/_jcr_content/par/cerosflex");

        servlet.doPost(request, response);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(jobManager).addJob(eq(CerosImportArchiveJobConsumer.TOPIC), payload.capture());
        assertEquals(ARCHIVE_NODE_PATH, payload.getValue().get(CerosImportArchiveJobConsumer.PROP_ARCHIVE_PATH));
        assertEquals("/content/page/jcr:content/par/cerosflex",
                payload.getValue().get(CerosImportArchiveJobConsumer.PROP_COMPONENT_PATH));
        assertNotNull(payload.getValue().get(CerosImportArchiveJobConsumer.PROP_JOB_ID));
        verify(response).setStatus(SlingHttpServletResponse.SC_ACCEPTED);
    }

    @Test
    void storesArchiveBinaryToVarImports() throws Exception {
        stubValidUpload();

        servlet.doPost(request, response);

        // archive bytes streamed into an nt:file under /var/ceros/imports
        verify(valueFactory).createBinary(any());
        verify(node).addNode("archive.tar.gz", "nt:file");
        verify(binary).dispose();
    }
}
