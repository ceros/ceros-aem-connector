package com.ceros.jobs;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;

/**
 * Scheduled cleanup for {@code /var/ceros/fetch-jobs} status nodes. Status
 * records aren't useful once a job has been done for a while and would
 * otherwise grow unbounded.
 */
@Component(
        service = Runnable.class,
        property = {
                "scheduler.expression=0 0 3 * * ?", // 03:00 daily
                "scheduler.concurrent:Boolean=false",
                "scheduler.runOn=LEADER"
        }
)
public class FetchJobCleanupTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FetchJobCleanupTask.class);
    private static final Duration MAX_AGE = Duration.ofHours(24);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public void run() {
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, CerosFetchManifestJobConsumer.SERVICE_USER))) {

            Resource base = resolver.getResource(JcrFetchProgress.FETCH_JOBS_BASE);
            if (base == null) {
                return;
            }

            Instant cutoff = Instant.now().minus(MAX_AGE);
            int removed = 0;
            for (Iterator<Resource> it = base.listChildren(); it.hasNext(); ) {
                Resource child = it.next();
                if (isExpired(child, cutoff)) {
                    resolver.delete(child);
                    removed++;
                }
            }
            if (removed > 0) {
                resolver.commit();
                log.info("Removed {} expired fetch-job status node(s)", removed);
            }
        } catch (LoginException e) {
            log.warn("Could not open service resource resolver for cleanup: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Fetch-job cleanup failed: {}", e.getMessage());
        }
    }

    private boolean isExpired(Resource child, Instant cutoff) {
        ValueMap props = child.getValueMap();
        String timestamp = props.get("finishedAt", String.class);
        if (timestamp == null) {
            timestamp = props.get("startedAt", String.class);
        }
        if (timestamp == null) {
            return false;
        }
        try {
            return Instant.parse(timestamp).isBefore(cutoff);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
