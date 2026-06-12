package com.ceros.servlets;

import com.ceros.CerosConstants;
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
import java.util.regex.Pattern;

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
    private static final Pattern MANIFEST_JSON_PATTERN =
            Pattern.compile("manifest(\\.v[0-9.]+)?\\.json$");

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

        String manifestUrl = StringUtils.trimToNull(request.getParameter("manifestUrl"));
        if (manifestUrl == null) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "manifestUrl parameter is required");
            return;
        }
        manifestUrl = normaliseManifestUrl(manifestUrl);

        try {
            cerosManifestService.validateManifestUrl(manifestUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected manifest URL {}: {}", manifestUrl, e.getMessage());
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        String componentPath = normaliseComponentPath(request.getParameter("componentPath"));

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

    private String normaliseComponentPath(String raw) {
        String componentPath = StringUtils.trimToNull(raw);
        if (componentPath == null) {
            return null;
        }
        // Sling encodes jcr:content as _jcr_content in URLs since colons aren't URL-safe.
        componentPath = componentPath.replace("_jcr_content", "jcr:content");
        if (!isComponentPathValid(componentPath)) {
            return null;
        }
        return componentPath;
    }

    private boolean isComponentPathValid(String path) {
        return path.startsWith("/content/")
                && !path.contains("..")
                && !path.contains("*");
    }

    private String normaliseManifestUrl(String manifestUrl) {
        if (!MANIFEST_JSON_PATTERN.matcher(manifestUrl).find()) {
            if (!manifestUrl.endsWith("/")) {
                manifestUrl += "/";
            }
            manifestUrl += CerosConstants.DEFAULT_ASSET_FILE_PATH;
        }
        return manifestUrl;
    }
}
