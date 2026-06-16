package com.ceros.servlets;

import com.ceros.delivery.modes.CerosDeliveryMode;
import com.ceros.delivery.modes.FetchDeliveryHandler;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.services.CerosManifestService;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

/**
 * Runs after a Ceros Flex component dialog is saved. When the component is in
 * inline mode, it grabs the {@code flex-client.js} URL from the manifest's
 * inline delivery mode and persists it as {@code cerosInlineScriptUrl}, so the
 * render path can emit the inline {@code <script>} with no network call.
 *
 * <p>This is the inline-mode analogue of Store mode's fetch step, but
 * lightweight — one manifest fetch, no asset download — so it runs inline with
 * the save rather than off a background job. Fail-soft: any problem is logged
 * and the property is cleared, so the save always succeeds (render then shows
 * the "inline unavailable" placeholder).
 */
@Component(service = SlingPostProcessor.class)
public class CerosFlexInlinePostProcessor implements SlingPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(CerosFlexInlinePostProcessor.class);

    private static final String RESOURCE_TYPE = "connectors/ceros/components/cerosflex";
    private static final String PROP_MODE = "cerosMode";
    private static final String PROP_MANIFEST_URL = "manifestUrl";
    private static final String PROP_INLINE_SCRIPT_URL = "cerosInlineScriptUrl";
    private static final String INLINE_DELIVERY_MODE = "inline";

    @Reference
    private CerosManifestService cerosManifestService;

    @Override
    public void process(SlingHttpServletRequest request, List<Modification> changes) {
        Resource resource = request.getResource();
        if (resource == null || !resource.isResourceType(RESOURCE_TYPE)) {
            return;
        }
        ModifiableValueMap props = resource.adaptTo(ModifiableValueMap.class);
        if (props == null) {
            return;
        }

        // Mode/URL reflect the values the SlingPostServlet just applied in this session.
        boolean inlineMode = CerosDeliveryMode.INLINE.value().equals(props.get(PROP_MODE, String.class));
        String manifestUrl = StringUtils.trimToNull(props.get(PROP_MANIFEST_URL, String.class));

        String resolved = inlineMode && manifestUrl != null ? grabInlineScriptUrl(manifestUrl) : null;

        String existing = props.get(PROP_INLINE_SCRIPT_URL, String.class);
        if (resolved != null) {
            if (!resolved.equals(existing)) {
                props.put(PROP_INLINE_SCRIPT_URL, resolved);
                changes.add(Modification.onModified(resource.getPath() + "/" + PROP_INLINE_SCRIPT_URL));
            }
        } else if (existing != null) {
            // Not inline, URL cleared, or grab failed — drop the stale value.
            props.remove(PROP_INLINE_SCRIPT_URL);
            changes.add(Modification.onModified(resource.getPath() + "/" + PROP_INLINE_SCRIPT_URL));
        }
    }

    /**
     * Fetches the manifest and returns its inline {@code flex-client.js} URL,
     * resolved against the manifest URL (a no-op for the absolute URLs the live
     * endpoint serves; absolutises a relative URL from an exported manifest).
     * Returns {@code null} on any failure — the caller treats that as "clear".
     */
    private String grabInlineScriptUrl(String manifestUrl) {
        String normalised = FetchDeliveryHandler.normaliseManifestUrl(manifestUrl);
        try {
            CerosManifestV1 manifest = cerosManifestService.fetchPublicManifestFromUrl(normalised);
            CerosManifestV1.DeliveryMode inline = manifest != null
                    ? manifest.getDeliveryMode(INLINE_DELIVERY_MODE) : null;
            if (inline == null || inline.getScripts().isEmpty()) {
                log.warn("Manifest {} has no inline delivery-mode script; clearing inline runtime URL", normalised);
                return null;
            }
            String scriptUrl = inline.getScripts().get(0).getUrl();
            if (StringUtils.isBlank(scriptUrl)) {
                return null;
            }
            return URI.create(normalised).resolve(scriptUrl).toString();
        } catch (Exception e) {
            log.warn("Could not grab inline runtime URL from {}: {}", normalised, e.getMessage());
            return null;
        }
    }
}
