package com.ceros.servlets;

import com.ceros.jobs.CerosImportArchiveJobConsumer;
import com.ceros.jobs.JcrFetchProgress;
import com.ceros.util.ServletUtils;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Authoring endpoint hit by the dialog's "Import" button in HTML-import mode.
 * Accepts a multipart upload of a Ceros {@code .tar.gz} export, streams it to a
 * transient node under {@code /var/ceros/imports/<jobId>}, then enqueues a Sling
 * Job to unpack + store it off the request thread. Returns {@code 202 Accepted}
 * with a job ID; the dialog polls the shared
 * {@code /bin/ceros/fetch-manifest-status} endpoint for completion.
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/ceros/import-archive",
        "sling.servlet.methods=POST"
    }
)
@Designate(ocd = CerosImportArchiveServlet.Config.class)
public class CerosImportArchiveServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(CerosImportArchiveServlet.class);

    static final String FILE_PARAM = "archive";
    static final String IMPORTS_BASE = "/var/ceros/imports";
    static final String ARCHIVE_NODE_NAME = "archive.tar.gz";

    @ObjectClassDefinition(name = "Ceros Import Archive Servlet")
    @interface Config {
        @AttributeDefinition(name = "Max archive size (bytes)",
                description = "Reject uploaded export archives larger than this. Note the "
                        + "Sling request upload limit may also need raising for large archives.")
        long maxArchiveBytes() default 52_428_800L; // 50 MB
    }

    @Reference
    private JobManager jobManager;

    private long maxArchiveBytes;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.maxArchiveBytes = config.maxArchiveBytes();
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        RequestParameter archive = request.getRequestParameter(FILE_PARAM);
        if (archive == null || archive.isFormField() || archive.getSize() <= 0) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "An archive file is required");
            return;
        }
        if (!hasTarGzName(archive.getFileName())) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Upload must be a Ceros export archive (.tar.gz)");
            return;
        }
        if (archive.getSize() > maxArchiveBytes) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Archive is too large (" + archive.getSize() + " bytes; max " + maxArchiveBytes + ")");
            return;
        }

        String componentPath = ServletUtils.normaliseComponentPath(request.getParameter("componentPath"));

        String jobId = UUID.randomUUID().toString();
        String archivePath;
        try {
            archivePath = storeArchive(request.getResourceResolver(), jobId, archive);
        } catch (Exception e) {
            log.error("Could not store uploaded import archive for job {}: {}", jobId, e.getMessage(), e);
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not store the uploaded archive");
            return;
        }

        JcrFetchProgress.seed(request.getResourceResolver(), jobId, null, componentPath);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(CerosImportArchiveJobConsumer.PROP_JOB_ID, jobId);
        payload.put(CerosImportArchiveJobConsumer.PROP_ARCHIVE_PATH, archivePath);
        if (componentPath != null) {
            payload.put(CerosImportArchiveJobConsumer.PROP_COMPONENT_PATH, componentPath);
        }

        Job job = jobManager.addJob(CerosImportArchiveJobConsumer.TOPIC, payload);
        if (job == null) {
            log.error("Failed to enqueue import-archive job for {}", archivePath);
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not enqueue import job");
            return;
        }

        log.info("Enqueued import-archive job {} for {}", jobId, archivePath);

        response.setStatus(SlingHttpServletResponse.SC_ACCEPTED);
        ServletUtils.writeJson(response, Map.of(
                "status", "accepted",
                "jobId", jobId,
                "statusUrl", "/bin/ceros/fetch-manifest-status.json?jobId=" + jobId));
    }

    private static boolean hasTarGzName(String fileName) {
        if (fileName == null) {
            return false;
        }
        // Some browsers send a full path; reduce to the basename first.
        String base = fileName.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.toLowerCase(Locale.ROOT);
        return base.endsWith(".tar.gz") || base.endsWith(".tgz");
    }

    /**
     * Streams the upload to {@code /var/ceros/imports/<jobId>/archive.tar.gz} as
     * an {@code nt:file}. Uses the request resolver — the same trust model as
     * {@link JcrFetchProgress#seed}, which already writes under {@code /var/ceros}.
     *
     * @return the JCR path of the stored archive file node
     */
    private String storeArchive(ResourceResolver resolver, String jobId, RequestParameter archive)
            throws Exception {
        Session session = resolver.adaptTo(Session.class);
        if (session == null) {
            throw new IOException("No JCR session available");
        }
        Node jobFolder = ensureFolder(session, IMPORTS_BASE + "/" + jobId);
        Node file = jobFolder.addNode(ARCHIVE_NODE_NAME, "nt:file");
        Node content = file.addNode("jcr:content", "nt:resource");
        Binary binary = session.getValueFactory().createBinary(archive.getInputStream());
        try {
            content.setProperty("jcr:data", binary);
            content.setProperty("jcr:mimeType", "application/gzip");
            content.setProperty("jcr:lastModified", Calendar.getInstance());
            session.save();
        } finally {
            binary.dispose();
        }
        return file.getPath();
    }

    private static Node ensureFolder(Session session, String absPath) throws Exception {
        Node current = session.getRootNode();
        for (String segment : absPath.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            current = current.hasNode(segment)
                    ? current.getNode(segment)
                    : current.addNode(segment, "sling:Folder");
        }
        return current;
    }
}
