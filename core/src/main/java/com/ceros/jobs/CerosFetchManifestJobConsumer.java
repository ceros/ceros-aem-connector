package com.ceros.jobs;

import com.ceros.services.CerosManifestService;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Background consumer for the {@code com/ceros/fetch-manifest} topic. Runs the
 * full fetch + asset upload + persist pipeline off the request thread so the
 * dialog stays responsive even on multi-page experiences with HLS video.
 *
 * <p>No automatic retries: failures here typically mean a bad URL, a CDN that
 * is gone, or a permissions problem — none of which retry will fix. The
 * author sees the failure via the status servlet and re-clicks Fetch.</p>
 */
@Component(
        service = JobConsumer.class,
        property = {
                JobConsumer.PROPERTY_TOPICS + "=" + CerosFetchManifestJobConsumer.TOPIC
        }
)
public class CerosFetchManifestJobConsumer implements JobConsumer {

    public static final String TOPIC = "com/ceros/fetch-manifest";

    public static final String PROP_JOB_ID = "jobId";
    public static final String PROP_MANIFEST_URL = "manifestUrl";
    public static final String PROP_COMPONENT_PATH = "componentPath";

    static final String SERVICE_USER = "ceros-fetcher";

    private static final Logger log = LoggerFactory.getLogger(CerosFetchManifestJobConsumer.class);

    @Reference
    private CerosManifestService cerosManifestService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public JobResult process(Job job) {
        String jobId = (String) job.getProperty(PROP_JOB_ID);
        String manifestUrl = (String) job.getProperty(PROP_MANIFEST_URL);
        String componentPath = (String) job.getProperty(PROP_COMPONENT_PATH);

        if (jobId == null || manifestUrl == null) {
            log.error("Fetch-manifest job missing required properties (jobId={}, manifestUrl={})",
                    jobId, manifestUrl);
            return JobResult.CANCEL;
        }

        ResourceResolver resolver = null;
        try {
            resolver = resourceResolverFactory.getServiceResourceResolver(
                    Map.of(ResourceResolverFactory.SUBSERVICE, SERVICE_USER));
        } catch (Throwable t) {
            // Includes LoginException (missing service user mapping) and any
            // runtime error inside the user store. We can't update the status
            // node without a resolver, so the status servlet's stale-pending
            // guard takes over — the dialog will see "failed" instead of
            // polling forever.
            log.error("Could not open service resource resolver for fetch-manifest job {}: {}",
                    jobId, t.getMessage(), t);
            return JobResult.CANCEL;
        }

        try {
            JcrFetchProgress progress = new JcrFetchProgress(resolver, jobId);
            try {
                cerosManifestService.performFetchAndStore(manifestUrl, componentPath, progress, resolver);
                return JobResult.OK;
            } catch (Throwable t) {
                log.error("Fetch-manifest job {} failed for {}: {}", jobId, manifestUrl, t.getMessage(), t);
                progress.onError(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                return JobResult.CANCEL;
            }
        } finally {
            resolver.close();
        }
    }
}
