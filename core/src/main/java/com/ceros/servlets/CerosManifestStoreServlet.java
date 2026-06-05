package com.ceros.servlets;

import com.ceros.CerosConstants;
import com.ceros.models.cerosflex.CerosManifestV0;
import com.ceros.models.cerosflex.StoredManifestBundle;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.CerosManifestService;
import com.ceros.util.ServletUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Authoring endpoint hit by the dialog's "Fetch" button. Fetches the primary
 * manifest plus every linked page's manifest, uploads every page's assets to
 * DAM, and persists the resulting {@link StoredManifestBundle} on the
 * component so the SSR router can serve any page fully offline.
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

    @Reference
    private CerosManifestService cerosManifestService;

    @Reference
    private CerosAssetStorageService cerosAssetStorageService;

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

        StoredManifestBundle bundle;
        try {
            bundle = cerosManifestService.fetchManifestBundle(manifestUrl);
        } catch (IllegalArgumentException e) {
            // URL failed the service-level allowlist (scheme/host policy).
            log.warn("Rejected manifest URL {}: {}", manifestUrl, e.getMessage());
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (IOException e) {
            log.error("Failed to fetch Ceros manifest from {}: {}", manifestUrl, e.getMessage(), e);
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_GATEWAY, e.getMessage());
            return;
        }

        Map<String, String> urlMap;
        try {
            urlMap = uploadAllPageAssets(bundle, request);
        } catch (Exception e) {
            log.error("Asset upload to DAM failed: {}", e.getMessage(), e);
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to upload assets to DAM");
            return;
        }

        boolean saved = persistBundleIfRequested(request, manifestUrl, bundle, urlMap);

        String fetchedAt = java.time.Instant.now().toString();
        ServletUtils.writeJson(response, Map.of(
                "status", "ok",
                "fetchedAt", fetchedAt,
                "saved", saved,
                "pages", bundle.getPagesBySlug().size()));
    }

    private Map<String, String> uploadAllPageAssets(StoredManifestBundle bundle,
                                                    SlingHttpServletRequest request) throws IOException {
        Map<String, String> combined = new LinkedHashMap<>();
        for (CerosManifestV0 manifest : bundle.getPagesBySlug().values()) {
            combined.putAll(cerosAssetStorageService.uploadAssets(manifest, request.getResourceResolver()));
        }
        return combined;
    }

    private boolean persistBundleIfRequested(SlingHttpServletRequest request,
                                             String manifestUrl,
                                             StoredManifestBundle bundle,
                                             Map<String, String> urlMap) {
        // Sling encodes jcr:content as _jcr_content in URLs since colons aren't URL-safe.
        String componentPath = StringUtils.trimToNull(request.getParameter("componentPath"));
        if (componentPath == null) {
            return false;
        }
        componentPath = componentPath.replace("_jcr_content", "jcr:content");
        if (!isComponentPathValid(componentPath)) {
            return false;
        }
        return cerosManifestService.storeManifestBundle(
                request.getResourceResolver(), componentPath, manifestUrl, bundle, urlMap);
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
