package com.ceros.servlets;

import com.ceros.jobs.CerosFetchManifestJobConsumer;
import com.ceros.jobs.JcrFetchProgress;
import com.ceros.services.CerosManifestService;
import com.ceros.util.ServletUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Authoring endpoint hit by the dialog's "Fetch" button. Validates input
 * synchronously, then enqueues a Sling Job to run the fetch + asset upload +
 * persist pipeline off the request thread. Returns {@code 202 Accepted} with
 * a job ID; the dialog polls {@code /bin/ceros/fetch-manifest-status} for
 * completion.
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/ceros/fetch-manifest",
        "sling.servlet.methods=POST"
    }
)
public class CerosManifestStoreServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(CerosManifestStoreServlet.class);

    static final String STATUS_URL_PATH = "/bin/ceros/fetch-manifest-status";

    @Reference
    private CerosManifestService cerosManifestService;

    @Reference
    private JobManager jobManager;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String incomingUrl = StringUtils.trimToNull(request.getParameter("manifestUrl"));
        if (incomingUrl == null) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "manifestUrl parameter is required");
            return;
        }

        // Resolve the pasted URL into a trusted, Ceros-owned manifest URL. A
        // vanity domain is supported by reading its x-flex-manifest header; any
        // URL that doesn't resolve to a Ceros-owned manifest is rejected here,
        // before anything is fetched or injected.
        String manifestUrl;
        try {
            manifestUrl = cerosManifestService.resolveTrustedManifestUrl(incomingUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected manifest URL {}: {}", incomingUrl, e.getMessage());
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (IOException e) {
            log.warn("Could not reach experience to verify {}: {}", incomingUrl, e.getMessage());
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_GATEWAY,
                    "Could not reach the experience to verify it. Please try again in a moment.");
            return;
        }

        String componentPath = ServletUtils.normaliseComponentPath(request.getParameter("componentPath"));

        String jobId = UUID.randomUUID().toString();
        JcrFetchProgress.seed(request.getResourceResolver(), jobId, manifestUrl, componentPath);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(CerosFetchManifestJobConsumer.PROP_JOB_ID, jobId);
        payload.put(CerosFetchManifestJobConsumer.PROP_MANIFEST_URL, manifestUrl);
        if (componentPath != null) {
            payload.put(CerosFetchManifestJobConsumer.PROP_COMPONENT_PATH, componentPath);
        }

        Job job = jobManager.addJob(CerosFetchManifestJobConsumer.TOPIC, payload);
        if (job == null) {
            log.error("Failed to enqueue fetch-manifest job for {}", manifestUrl);
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not enqueue fetch job");
            return;
        }

        log.info("Enqueued fetch-manifest job {} for {}", jobId, manifestUrl);

        response.setStatus(SlingHttpServletResponse.SC_ACCEPTED);
        ServletUtils.writeJson(response, Map.of(
                "status", "accepted",
                "jobId", jobId,
                "statusUrl", STATUS_URL_PATH + ".json?jobId=" + jobId));
    }
}
