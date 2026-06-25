package com.ceros.servlets;

import com.ceros.CerosConstants;
import com.ceros.delivery.modes.CerosDeliveryMode;
import com.ceros.models.cerosflex.CerosManifestV1;
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
import java.util.Map;

/**
 * Authoring endpoint the Ceros Flex dialog calls when the author clicks
 * <em>Done</em>, to validate the pasted experience URL <em>before</em> the save
 * goes through — so every delivery mode gives the same up-front feedback the
 * Store mode's Fetch button already does. Mirrors the per-mode checks of
 * {@link CerosFlexInlinePostProcessor} (the server-side gate that ultimately
 * enforces this), returning the same human-readable messages so the dialog can
 * surface them in a toast.
 *
 * <p>Returns {@code 200 {valid:true, manifestUrl}} when the URL resolves to a
 * trusted, Ceros-owned manifest; {@code 400 {message}} when it isn't a
 * recognized Ceros experience (or, for inline mode, exposes no inline delivery
 * mode); {@code 502 {message}} when the experience can't be reached.
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/ceros/validate-manifest-url",
        "sling.servlet.methods=POST"
    }
)
public class CerosManifestValidateServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(CerosManifestValidateServlet.class);

    private static final String INLINE_DELIVERY_MODE = "inline";

    @Reference
    private transient CerosManifestService cerosManifestService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pastedUrl = StringUtils.trimToNull(request.getParameter("manifestUrl"));
        if (pastedUrl == null) {
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "manifestUrl parameter is required");
            return;
        }
        String mode = StringUtils.trimToNull(request.getParameter("cerosMode"));

        try {
            String manifestUrl = cerosManifestService.resolveTrustedManifestUrl(pastedUrl);
            // Inline mode needs a usable inline delivery mode, same as the gate.
            if (CerosDeliveryMode.INLINE.value().equals(mode)) {
                requireInlineDeliveryMode(manifestUrl);
            }
            ServletUtils.writeJson(response, Map.of("valid", true, "manifestUrl", manifestUrl));
        } catch (IllegalArgumentException e) {
            log.debug("Rejected manifest URL {}: {}", pastedUrl, e.getMessage());
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            log.debug("Could not reach experience to verify {}: {}", pastedUrl, e.getMessage());
            ServletUtils.writeError(response, SlingHttpServletResponse.SC_BAD_GATEWAY,
                    CerosConstants.MSG_UNREACHABLE_EXPERIENCE);
        }
    }

    private void requireInlineDeliveryMode(String manifestUrl) throws IOException {
        CerosManifestV1 manifest = cerosManifestService.fetchPublicManifestFromUrl(manifestUrl);
        CerosManifestV1.DeliveryMode inline = manifest != null
                ? manifest.getDeliveryMode(INLINE_DELIVERY_MODE) : null;
        String scriptUrl = inline != null && !inline.getScripts().isEmpty()
                ? inline.getScripts().get(0).getUrl() : null;
        if (StringUtils.isBlank(scriptUrl)) {
            throw new IllegalArgumentException(CerosConstants.MSG_NO_INLINE_MODE);
        }
    }
}
