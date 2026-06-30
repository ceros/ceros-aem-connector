package com.ceros.servlets;

import com.ceros.CerosConstants;
import com.ceros.delivery.modes.CerosDeliveryMode;
import com.ceros.models.cerosflex.CerosManifestV1;
import com.ceros.services.CerosManifestService;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Runs after a Ceros Flex component dialog is saved and validates the pasted
 * experience URL for every URL-based delivery mode (inline, fetch, embed and
 * store). {@code resolveTrustedManifestUrl} confirms the URL resolves to a
 * trusted, Ceros-owned manifest — supporting vanity domains via the
 * {@code x-flex-manifest} header — and an untrusted or unreachable URL makes
 * the post-processor throw, which aborts the Sling POST so the dialog refuses
 * to save: an invalid URL can never be persisted.
 *
 * <p>On success the live modes (inline and fetch) additionally have their
 * manifest URL canonicalised to the resolved Ceros host (so render trusts the
 * stored URL and makes no extra network call), and inline mode grabs the
 * {@code flex-client.js} URL from the manifest's inline delivery mode and
 * persists it as {@code cerosInlineScriptUrl} for the render path. Embed mode
 * is validated only — its (possibly vanity) URL is left as pasted, since the
 * experience is loaded in a client-side iframe rather than fetched server-side.
 *
 * <p>This is the live-mode analogue of Store mode's fetch step, but lightweight
 * — at most one HEAD + one manifest fetch, no asset download — so it runs inline
 * with the save rather than off a background job.
 */
@Component(service = SlingPostProcessor.class)
public class CerosFlexManifestUrlPostProcessor implements SlingPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(CerosFlexManifestUrlPostProcessor.class);

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

        // A dialog save uses the default modify operation. Delete, move and copy
        // also POST to this resource — skip them so e.g. deleting the component
        // never fails on manifest-URL validation (which would otherwise run
        // against the node being removed and abort the operation).
        String operation = request.getParameter(SlingPostConstants.RP_OPERATION);
        if (operation != null && !SlingPostConstants.OPERATION_MODIFY.equals(operation)) {
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
        boolean embedMode = CerosDeliveryMode.EMBED.value().equals(mode);
        boolean storeMode = CerosDeliveryMode.STORE.value().equals(mode);
        boolean urlMode = inlineMode || fetchMode || embedMode || storeMode;
        String manifestUrl = StringUtils.trimToNull(props.get(PROP_MANIFEST_URL, String.class));

        // Validate the pasted URL on save for every URL-based mode. A failure
        // throws, which aborts the Sling POST so the dialog won't save an
        // untrusted or unreachable experience. (Import mode has no URL.)
        String scriptUrl = null;
        if (urlMode && manifestUrl != null) {
            String canonical = resolveOrReject(manifestUrl);

            // The live modes fetch/inject server-side, so they must point at the
            // canonical Ceros host; embed and store keep the pasted URL (embed
            // loads it in an iframe; store resolves it via its own Fetch action).
            if ((inlineMode || fetchMode) && !canonical.equals(manifestUrl)) {
                props.put(PROP_MANIFEST_URL, canonical);
                changes.add(Modification.onModified(resource.getPath() + "/" + PROP_MANIFEST_URL));
            }

            // Inline mode also grabs the flex-client.js runtime URL to persist
            // for the render path.
            if (inlineMode) {
                scriptUrl = grabInlineScriptUrl(canonical);
            }
        }

        writeScriptUrl(props, changes, resource, scriptUrl);
    }

    /**
     * Resolves the pasted URL to a trusted, Ceros-owned manifest URL, throwing a
     * user-facing {@link IllegalArgumentException} when it isn't a recognized
     * Ceros experience (propagating the resolver's own message) or can't be
     * reached. The thrown exception aborts the save.
     */
    private String resolveOrReject(String manifestUrl) {
        try {
            return cerosManifestService.resolveTrustedManifestUrl(manifestUrl);
        } catch (IllegalArgumentException e) {
            // Not https / IP-literal / not a recognized Ceros domain.
            log.warn("Rejected manifest URL {}: {}", manifestUrl, e.getMessage());
            throw e;
        } catch (IOException e) {
            log.warn("Could not reach experience to verify manifest URL {}: {}", manifestUrl, e.getMessage());
            throw new IllegalArgumentException(CerosConstants.MSG_UNREACHABLE_EXPERIENCE);
        }
    }

    /**
     * Fetches the (already trusted) manifest and returns its inline
     * {@code flex-client.js} URL, resolved against the manifest URL — a no-op for
     * the absolute URLs the live endpoint serves; absolutises a relative URL from
     * an exported manifest. Returns {@code null} when the manifest exposes no
     * inline script (nothing is persisted then). Throws
     * {@link IllegalArgumentException} (aborting the save) only when the
     * experience can't be reached.
     */
    private String grabInlineScriptUrl(String canonical) {
        CerosManifestV1 manifest;
        try {
            manifest = cerosManifestService.fetchPublicManifestFromUrl(canonical);
        } catch (IOException e) {
            log.warn("Could not fetch manifest to grab inline runtime URL from {}: {}", canonical, e.getMessage());
            throw new IllegalArgumentException(CerosConstants.MSG_UNREACHABLE_EXPERIENCE);
        }
        CerosManifestV1.DeliveryMode inline = manifest != null
                ? manifest.getDeliveryMode(INLINE_DELIVERY_MODE) : null;
        String scriptUrl = inline != null && !inline.getScripts().isEmpty()
                ? inline.getScripts().get(0).getUrl() : null;
        if (StringUtils.isBlank(scriptUrl)) {
            log.warn("Manifest {} has no inline delivery-mode script", canonical);
            return null;
        }
        return URI.create(canonical).resolve(scriptUrl).toString();
    }

    /**
     * Keeps the persisted inline runtime URL in sync with the latest save: sets
     * it when present, clears a stale value otherwise (e.g. after switching away
     * from inline mode), recording a modification only when it actually changes.
     */
    private void writeScriptUrl(ModifiableValueMap props, List<Modification> changes,
                                Resource resource, String scriptUrl) {
        String existing = props.get(PROP_INLINE_SCRIPT_URL, String.class);
        if (scriptUrl != null) {
            if (!scriptUrl.equals(existing)) {
                props.put(PROP_INLINE_SCRIPT_URL, scriptUrl);
                changes.add(Modification.onModified(resource.getPath() + "/" + PROP_INLINE_SCRIPT_URL));
            }
        } else if (existing != null) {
            props.remove(PROP_INLINE_SCRIPT_URL);
            changes.add(Modification.onModified(resource.getPath() + "/" + PROP_INLINE_SCRIPT_URL));
        }
    }
}
