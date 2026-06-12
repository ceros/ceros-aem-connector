package com.ceros.jobs;

import com.ceros.services.CerosManifestService;
import com.ceros.services.FetchProgress;
import org.apache.sling.api.resource.LoginException;
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
class CerosFetchManifestJobConsumerTest {

    @Mock private CerosManifestService manifestService;
    @Mock private ResourceResolverFactory resourceResolverFactory;
    @Mock private ResourceResolver resolver;
    @Mock private Job job;

    private CerosFetchManifestJobConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        consumer = new CerosFetchManifestJobConsumer();
        setField("cerosManifestService", manifestService);
        setField("resourceResolverFactory", resourceResolverFactory);

        lenient().when(resourceResolverFactory.getServiceResourceResolver(anyMap()))
                .thenReturn(resolver);
        lenient().when(job.getProperty("jobId")).thenReturn("job-1");
        lenient().when(job.getProperty("manifestUrl")).thenReturn("https://example.com/m.json");
        lenient().when(job.getProperty("componentPath")).thenReturn("/content/x");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosFetchManifestJobConsumer.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(consumer, value);
    }

    @Test
    void processReturnsOkOnSuccess() throws Exception {
        JobResult result = consumer.process(job);

        assertEquals(JobResult.OK, result);
        verify(manifestService).performFetchAndStore(
                eq("https://example.com/m.json"),
                eq("/content/x"),
                any(FetchProgress.class),
                eq(resolver));
        verify(resolver).close();
    }

    @Test
    void processReturnsCancelOnFetchFailure() throws Exception {
        doThrow(new IOException("boom"))
                .when(manifestService).performFetchAndStore(anyString(), any(), any(), any());

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(resolver).close();
    }

    @Test
    void processReturnsCancelWhenServiceResolverUnavailable() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(anyMap()))
                .thenThrow(new LoginException("no user"));

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(manifestService, never()).performFetchAndStore(any(), any(), any(), any());
    }

    @Test
    void processReturnsCancelOnUnexpectedResolverError() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(anyMap()))
                .thenThrow(new RuntimeException("oak segment gone"));

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(manifestService, never()).performFetchAndStore(any(), any(), any(), any());
    }

    @Test
    void processReturnsCancelOnRuntimeFailureFromService() throws Exception {
        doThrow(new RuntimeException("unexpected"))
                .when(manifestService).performFetchAndStore(anyString(), any(), any(), any());

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(resolver).close();
    }

    @Test
    void processReturnsCancelWhenJobIdMissing() throws Exception {
        when(job.getProperty("jobId")).thenReturn(null);

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
        verify(resourceResolverFactory, never()).getServiceResourceResolver(any());
    }

    @Test
    void processReturnsCancelWhenManifestUrlMissing() throws Exception {
        when(job.getProperty("manifestUrl")).thenReturn(null);

        JobResult result = consumer.process(job);

        assertEquals(JobResult.CANCEL, result);
    }

    @Test
    void processForwardsNullComponentPath() throws Exception {
        when(job.getProperty("componentPath")).thenReturn(null);

        JobResult result = consumer.process(job);

        assertEquals(JobResult.OK, result);
        verify(manifestService).performFetchAndStore(
                eq("https://example.com/m.json"),
                isNull(),
                any(FetchProgress.class),
                eq(resolver));
    }

    @Test
    void processUsesCerosFetcherSubservice() throws Exception {
        consumer.process(job);

        verify(resourceResolverFactory).getServiceResourceResolver(
                argThat((Map<String, Object> map) ->
                        "ceros-fetcher".equals(map.get(ResourceResolverFactory.SUBSERVICE))));
    }
}
