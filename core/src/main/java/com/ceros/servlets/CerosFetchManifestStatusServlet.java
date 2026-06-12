package com.ceros.servlets;

import com.ceros.services.FetchProgress;
import com.ceros.util.ServletUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Polled by the authoring dialog while a {@code /bin/ceros/fetch-manifest}
 * job is running. Reads the status node written by
 * {@code com.ceros.jobs.JcrFetchProgress} and returns its current state.
 */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/ceros/fetch-manifest-status",
                "sling.servlet.methods=GET"
        }
)
public class CerosFetchManifestStatusServlet extends SlingSafeMethodsServlet {

    static final String FETCH_JOBS_BASE = "/var/ceros/fetch-jobs";

    // If a job has been "pending" for longer than this without the consumer
    // recording any phase, treat it as failed in the response. Protects the
    // dialog from polling forever when the consumer crashed before it could
    // write status (e.g. missing service user, Oak segment errors).
    static final Duration STALE_PENDING_THRESHOLD = Duration.ofSeconds(60);

    // UUID v4 form; jobIds are generated server-side so this is just a
    // belt-and-braces guard against odd input slipping into a JCR path lookup.
    private static final Pattern JOB_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jobId = StringUtils.trimToNull(request.getParameter("jobId"));
        if (jobId == null || !JOB_ID_PATTERN.matcher(jobId).matches()) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "jobId parameter is required");
            return;
        }

        Resource resource = request.getResourceResolver().getResource(FETCH_JOBS_BASE + "/" + jobId);
        if (resource == null) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_NOT_FOUND,
                    "Unknown jobId: " + jobId);
            return;
        }

        ValueMap props = resource.getValueMap();
        String status = props.get("status", FetchProgress.STATUS_PENDING);
        String error = props.get("error", String.class);

        if (FetchProgress.STATUS_PENDING.equals(status) && isStalePending(props)) {
            status = FetchProgress.STATUS_FAILED;
            error = "Fetch job did not start. Check the AEM error log for the consumer failure.";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", jobId);
        body.put("status", status);
        copyIfPresent(props, body, "phase");
        copyIfPresent(props, body, "manifestUrl");
        copyIfPresent(props, body, "componentPath");
        copyIfPresent(props, body, "pagesProcessed");
        copyIfPresent(props, body, "pagesTotal");
        copyIfPresent(props, body, "startedAt");
        copyIfPresent(props, body, "finishedAt");
        copyIfPresent(props, body, "fetchedAt");
        copyIfPresent(props, body, "saved");
        if (error != null) {
            body.put("error", error);
        }

        ServletUtils.writeJson(response, body);
    }

    private static boolean isStalePending(ValueMap props) {
        // A real consumer always emits at least one onPhase() update before any
        // I/O work — the absence of phase plus a startedAt older than the
        // threshold means the consumer never made progress.
        if (props.containsKey("phase")) {
            return false;
        }
        String startedAt = props.get("startedAt", String.class);
        if (startedAt == null) {
            return false;
        }
        try {
            return Instant.parse(startedAt).isBefore(Instant.now().minus(STALE_PENDING_THRESHOLD));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static void copyIfPresent(ValueMap from, Map<String, Object> to, String key) {
        if (from.containsKey(key)) {
            to.put(key, from.get(key));
        }
    }
}
