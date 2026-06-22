package com.ceros.servlets;

import com.ceros.delivery.modes.CerosDeliveryMode;
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
 * Runs after a Ceros Flex component dialog is saved. For the live delivery
 * modes (inline and fetch) it validates the pasted manifest URL up front:
 * {@code resolveTrustedManifestUrl} resolves it to a trusted, Ceros-owned
 * manifest URL (supporting vanity domains via the {@code x-flex-manifest}
 * header) and persists that canonical URL back onto the component, so render
 * trusts the stored URL and makes no extra network call. For inline mode it
 * additionally grabs the {@code flex-client.js} URL from the manifest's inline
 * delivery mode and persists it as {@code cerosInlineScriptUrl}, so the render
 * path can emit the inline {@code <script>} with no network call.
 *
 * <p>This is the live-mode analogue of Store mode's fetch step, but lightweight
 * — at most one HEAD + one manifest fetch, no asset download — so it runs inline
 * with the save rather than off a background job. Fail-soft: any problem
 * (untrusted host, unreachable page, no inline delivery mode) is logged and the
 * inline script URL is cleared, so the save always succeeds and an untrusted
 * experience renders the placeholder rather than injecting a non-Ceros script.
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
        String mode = props.get(PROP_MODE, String.class);
        boolean inlineMode = CerosDeliveryMode.INLINE.value().equals(mode);
        boolean fetchMode = CerosDeliveryMode.FETCH.value().equals(mode);
        String manifestUrl = StringUtils.trimToNull(props.get(PROP_MANIFEST_URL, String.class));

        // Validate the pasted URL and canonicalise it for the live modes at save
        // time, so render trusts the stored URL. null = untrusted/unreachable.
        String canonical = (inlineMode || fetchMode) && manifestUrl != null
                ? canonicaliseManifestUrl(props, changes, resource, manifestUrl)
                : null;

        // Inline mode additionally grabs the flex-client.js runtime URL up front.
        String scriptUrl = inlineMode && canonical != null ? grabInlineScriptUrl(canonical) : null;

        String existing = props.get(PROP_INLINE_SCRIPT_URL, String.class);
        if (scriptUrl != null) {
            if (!scriptUrl.equals(existing)) {
                props.put(PROP_INLINE_SCRIPT_URL, scriptUrl);
                changes.add(Modification.onModified(resource.getPath() + "/" + PROP_INLINE_SCRIPT_URL));
            }
        } else if (existing != null) {
            // Not inline, URL cleared, or grab failed — drop the stale value.
            props.remove(PROP_INLINE_SCRIPT_URL);
            changes.add(Modification.onModified(resource.getPath() + "/" + PROP_INLINE_SCRIPT_URL));
        }
    }

    /**
     * Resolves the pasted manifest URL to a trusted, Ceros-owned manifest URL
     * (supporting vanity domains via {@code x-flex-manifest}) and persists the
     * canonical URL back onto the component, so the browser's
     * {@code data-flex-manifest-url} / the live fetch points at Ceros and not the
     * (possibly attacker-influenced) vanity host. Returns {@code null} when the
     * URL can't be resolved to a Ceros-owned manifest — render then refuses it
     * via the whitelist gate rather than fetching anything off-Ceros.
     */
    private String canonicaliseManifestUrl(ModifiableValueMap props, List<Modification> changes,
                                           Resource resource, String manifestUrl) {
        String canonical;
        try {
            canonical = cerosManifestService.resolveTrustedManifestUrl(manifestUrl);
        } catch (Exception e) {
            log.warn("Could not resolve a trusted manifest URL from {}: {}", manifestUrl, e.getMessage());
            return null;
        }
        if (!canonical.equals(manifestUrl)) {
            props.put(PROP_MANIFEST_URL, canonical);
            changes.add(Modification.onModified(resource.getPath() + "/" + PROP_MANIFEST_URL));
        }
        return canonical;
    }

    /**
     * Fetches the (already trusted) manifest and returns its inline
     * {@code flex-client.js} URL, resolved against the manifest URL — a no-op for
     * the absolute URLs the live endpoint serves; absolutises a relative URL from
     * an exported manifest. Returns {@code null} on any failure (unreachable page,
     * no inline delivery mode), which the caller treats as "clear".
     */
    private String grabInlineScriptUrl(String canonical) {
        try {
            CerosManifestV1 manifest = cerosManifestService.fetchPublicManifestFromUrl(canonical);
            CerosManifestV1.DeliveryMode inline = manifest != null
                    ? manifest.getDeliveryMode(INLINE_DELIVERY_MODE) : null;
            if (inline == null || inline.getScripts().isEmpty()) {
                log.warn("Manifest {} has no inline delivery-mode script; clearing inline runtime URL", canonical);
                return null;
            }
            String scriptUrl = inline.getScripts().get(0).getUrl();
            if (StringUtils.isBlank(scriptUrl)) {
                return null;
            }
            return URI.create(canonical).resolve(scriptUrl).toString();
        } catch (Exception e) {
            log.warn("Could not grab inline runtime URL from {}: {}", canonical, e.getMessage());
            return null;
        }
    }
}
