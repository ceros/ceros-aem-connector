package com.ceros.jobs;

import com.ceros.services.FetchProgress;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Session;
import java.time.Instant;

/**
 * {@link FetchProgress} that writes status updates to
 * {@code /var/ceros/fetch-jobs/<jobId>} so the status servlet can poll them.
 *
 * <p>Each callback commits its own session save. Failures are logged but not
 * propagated — losing a progress write must not break the underlying fetch job.</p>
 */
public final class JcrFetchProgress implements FetchProgress {

    static final String FETCH_JOBS_BASE = "/var/ceros/fetch-jobs";

    private static final Logger log = LoggerFactory.getLogger(JcrFetchProgress.class);

    private final ResourceResolver resolver;
    private final String jobId;

    public JcrFetchProgress(ResourceResolver resolver, String jobId) {
        this.resolver = resolver;
        this.jobId = jobId;
    }

    /**
     * Seeds the status node before the job consumer fires.
     *
     * <p>Called from the request thread so the status servlet can return
     * {@code pending} immediately after a POST, even before the job runs.</p>
     */
    public static void seed(ResourceResolver resolver, String jobId, String manifestUrl,
                            String componentPath) {
        try {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                return;
            }
            Node node = ensureNode(session, jobId);
            node.setProperty("status", STATUS_PENDING);
            node.setProperty("startedAt", Instant.now().toString());
            if (manifestUrl != null) {
                node.setProperty("manifestUrl", manifestUrl);
            }
            if (componentPath != null) {
                node.setProperty("componentPath", componentPath);
            }
            session.save();
        } catch (Exception e) {
            log.warn("Could not seed fetch-job status node for {}: {}", jobId, e.getMessage());
        }
    }

    @Override
    public void onPhase(String phase) {
        update(node -> {
            node.setProperty("status", STATUS_RUNNING);
            node.setProperty("phase", phase);
        });
    }

    @Override
    public void onPageProgress(int processed, int total) {
        update(node -> {
            node.setProperty("pagesProcessed", processed);
            node.setProperty("pagesTotal", total);
        });
    }

    @Override
    public void onComplete(String fetchedAt, boolean saved, int pages) {
        update(node -> {
            node.setProperty("status", STATUS_SUCCESS);
            node.setProperty("fetchedAt", fetchedAt);
            node.setProperty("saved", saved);
            node.setProperty("pagesTotal", pages);
            node.setProperty("pagesProcessed", pages);
            node.setProperty("finishedAt", Instant.now().toString());
        });
    }

    @Override
    public void onError(String message) {
        update(node -> {
            node.setProperty("status", STATUS_FAILED);
            node.setProperty("error", message == null ? "Unknown error" : message);
            node.setProperty("finishedAt", Instant.now().toString());
        });
    }

    private void update(NodeMutation mutation) {
        try {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                return;
            }
            Node node = ensureNode(session, jobId);
            mutation.apply(node);
            session.save();
        } catch (Exception e) {
            log.warn("Could not update fetch-job status for {}: {}", jobId, e.getMessage());
        }
    }

    private static Node ensureNode(Session session, String jobId) throws Exception {
        Node parent = ensurePath(session, FETCH_JOBS_BASE);
        if (parent.hasNode(jobId)) {
            return parent.getNode(jobId);
        }
        return parent.addNode(jobId, JcrConstants.NT_UNSTRUCTURED);
    }

    private static Node ensurePath(Session session, String absPath) throws Exception {
        if (session.nodeExists(absPath)) {
            return session.getNode(absPath);
        }
        String[] segments = absPath.split("/");
        Node current = session.getRootNode();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            current = current.hasNode(segment)
                    ? current.getNode(segment)
                    : current.addNode(segment, "sling:Folder");
        }
        return current;
    }

    @FunctionalInterface
    private interface NodeMutation {
        void apply(Node node) throws Exception;
    }
}
