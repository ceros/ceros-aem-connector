package com.ceros.jobs;

import com.ceros.services.CerosManifestService;
import com.ceros.services.FetchProgress;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer.JobResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerosImportArchiveJobConsumerTest {

    private static final String ARCHIVE_PATH = "/var/ceros/imports/job-1/archive.tar.gz";

    @Mock private CerosManifestService manifestService;
    @Mock private ResourceResolverFactory resourceResolverFactory;
    @Mock private ResourceResolver resolver;
    @Mock private Job job;
    @Mock private Resource archiveResource;
    @Mock private Resource jobFolderResource;

    private CerosImportArchiveJobConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        consumer = new CerosImportArchiveJobConsumer();
        setField("cerosManifestService", manifestService);
        setField("resourceResolverFactory", resourceResolverFactory);

        lenient().when(resourceResolverFactory.getServiceResourceResolver(anyMap()))
                .thenReturn(resolver);
        lenient().when(job.getProperty("jobId")).thenReturn("job-1");
        lenient().when(job.getProperty("archivePath")).thenReturn(ARCHIVE_PATH);
        lenient().when(job.getProperty("componentPath")).thenReturn("/content/x");
        lenient().when(resolver.getResource(ARCHIVE_PATH)).thenReturn(archiveResource);
        lenient().when(archiveResource.getParent()).thenReturn(jobFolderResource);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosImportArchiveJobConsumer.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(consumer, value);
    }

    @Test
    void processReturnsOkAndDeletesArchiveOnSuccess() throws Exception {
        JobResult result = consumer.process(job);

        assertEquals(JobResult.OK, result);
        verify(manifestService).performImportAndStore(
                eq(ARCHIVE_PATH), eq("/content/x"), any(FetchProgress.class), eq(resolver));
        verify(resolver).delete(jobFolderResource);
        verify(resolver).close();
    }

    @Test
    void processReturnsCancelOnImportFailure() throws Exception {
        doThrow(new IOException("bad archive"))
                .when(manifestService).performImportAndStore(anyString(), any(), any(), any());

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(resolver).close();
        verify(resolver, never()).delete(any(Resource.class));
    }

    @Test
    void processReturnsCancelWhenArchivePathMissing() throws Exception {
        when(job.getProperty("archivePath")).thenReturn(null);

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(resourceResolverFactory, never()).getServiceResourceResolver(any());
    }

    @Test
    void processReturnsCancelWhenJobIdMissing() throws Exception {
        when(job.getProperty("jobId")).thenReturn(null);

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(resourceResolverFactory, never()).getServiceResourceResolver(any());
    }

    @Test
    void processReturnsCancelWhenServiceResolverUnavailable() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(anyMap()))
                .thenThrow(new LoginException("no user"));

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(manifestService, never()).performImportAndStore(any(), any(), any(), any());
    }

    @Test
    void processForwardsNullComponentPath() throws Exception {
        when(job.getProperty("componentPath")).thenReturn(null);

        JobResult result = consumer.process(job);

        assertEquals(JobResult.OK, result);
        verify(manifestService).performImportAndStore(
                eq(ARCHIVE_PATH), isNull(), any(FetchProgress.class), eq(resolver));
    }

    @Test
    void processUsesCerosFetcherSubservice() throws Exception {
        consumer.process(job);

        verify(resourceResolverFactory).getServiceResourceResolver(
                argThat((Map<String, Object> map) ->
                        "ceros-fetcher".equals(map.get(ResourceResolverFactory.SUBSERVICE))));
    }
}
