package com.ceros.jobs;

import com.ceros.services.CerosManifestService;
import org.apache.sling.api.resource.Resource;
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
 * Background consumer for the {@code com/ceros/import-archive} topic. Unpacks an
 * uploaded Ceros export ({@code .tar.gz}) and stores it into JCR/DAM off the
 * request thread, mirroring {@link CerosFetchManifestJobConsumer}.
 *
 * <p>On success the transient uploaded archive under {@code /var/ceros/imports}
 * is deleted. No automatic retries: failures usually mean a malformed archive
 * or a permissions problem, neither of which a retry fixes — the author sees the
 * failure via the status servlet and re-uploads.</p>
 */
@Component(
        service = JobConsumer.class,
        property = {
                JobConsumer.PROPERTY_TOPICS + "=" + CerosImportArchiveJobConsumer.TOPIC
        }
)
public class CerosImportArchiveJobConsumer implements JobConsumer {

    public static final String TOPIC = "com/ceros/import-archive";

    public static final String PROP_JOB_ID = "jobId";
    public static final String PROP_ARCHIVE_PATH = "archivePath";
    public static final String PROP_COMPONENT_PATH = "componentPath";

    static final String SERVICE_USER = "ceros-fetcher";

    private static final Logger log = LoggerFactory.getLogger(CerosImportArchiveJobConsumer.class);

    @Reference
    private CerosManifestService cerosManifestService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public JobResult process(Job job) {
        String jobId = (String) job.getProperty(PROP_JOB_ID);
        String archivePath = (String) job.getProperty(PROP_ARCHIVE_PATH);
        String componentPath = (String) job.getProperty(PROP_COMPONENT_PATH);

        if (jobId == null || archivePath == null) {
            log.error("Import-archive job missing required properties (jobId={}, archivePath={})",
                    jobId, archivePath);
            return JobResult.CANCEL;
        }

        ResourceResolver resolver;
        try {
            resolver = resourceResolverFactory.getServiceResourceResolver(
                    Map.of(ResourceResolverFactory.SUBSERVICE, SERVICE_USER));
        } catch (Throwable t) {
            log.error("Could not open service resource resolver for import-archive job {}: {}",
                    jobId, t.getMessage(), t);
            return JobResult.CANCEL;
        }

        try {
            JcrFetchProgress progress = new JcrFetchProgress(resolver, jobId);
            try {
                cerosManifestService.performImportAndStore(archivePath, componentPath, progress, resolver);
                deleteArchive(resolver, archivePath);
                return JobResult.OK;
            } catch (Throwable t) {
                log.error("Import-archive job {} failed for {}: {}", jobId, archivePath, t.getMessage(), t);
                progress.onError(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                return JobResult.CANCEL;
            }
        } finally {
            resolver.close();
        }
    }

    /**
     * Removes the transient {@code /var/ceros/imports/<jobId>} folder once the
     * import has been persisted. Cleanup failures are logged, not propagated —
     * the import itself already succeeded.
     */
    private void deleteArchive(ResourceResolver resolver, String archivePath) {
        try {
            Resource archive = resolver.getResource(archivePath);
            Resource jobFolder = archive != null ? archive.getParent() : null;
            if (jobFolder != null) {
                resolver.delete(jobFolder);
                resolver.commit();
            }
        } catch (Exception e) {
            log.warn("Could not delete imported archive at {}: {}", archivePath, e.getMessage());
        }
    }
}
