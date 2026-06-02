package com.ceros.servlets;

import com.ceros.services.CerosAuthenticatedApiService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Map;

/**
 * Sling servlet that exposes the Ceros Flex API folder-tree to the authoring
 * dialog, allowing authors to browse and pick a published experience.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code /bin/ceros/flex-api/config} – returns whether the browse feature is enabled</li>
 *   <li>{@code /bin/ceros/flex-api/folder-tree} – returns the account's folder/experience tree</li>
 * </ul>
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/ceros/flex-api/config",
        "sling.servlet.paths=/bin/ceros/flex-api/folder-tree",
        "sling.servlet.methods=GET"
    }
)
public class CerosBrowseExperiencesServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(CerosBrowseExperiencesServlet.class);

    @Reference
    private CerosAuthenticatedApiService flexApiService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, no-store");

        String path = request.getRequestPathInfo().getResourcePath();

        if (path.endsWith("/config")) {
            ServletUtils.writeJson(response, Map.of("enabled", flexApiService.isEnabled()));
            return;
        }

        if (path.endsWith("/folder-tree")) {
            if (!flexApiService.isEnabled()) {
                ServletUtils.writeError(response, SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Flex API is not configured");
                return;
            }

            try {
                String json = flexApiService.getFolderTreeJson();
                response.getWriter().write(json);
            } catch (IOException e) {
                log.error("Failed to fetch folder tree from Ceros Flex API: {}", e.getMessage(), e);
                ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_GATEWAY, e.getMessage());
            }
            return;
        }

        ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                "Unknown endpoint. Use /config.json or /folder-tree.json");
    }
}
