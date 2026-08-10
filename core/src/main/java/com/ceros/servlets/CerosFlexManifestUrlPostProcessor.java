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
 * keeps its (possibly vanity) URL as pasted, since the experience is loaded in
 * a client-side iframe rather than fetched server-side.
 *
 * <p>Every URL-based mode also reads the experience's resource ID out of the
 * manifest and persists it as {@code cerosFlexExperienceResourceId}. That is
 * metadata for repository queries, not something delivery reads, so failing to
 * obtain it never fails the save — the property is simply cleared. Inline mode
 * is the exception, and only because it independently requires the manifest.
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
    private static final String PROP_EXPERIENCE_RESOURCE_ID = "cerosFlexExperienceResourceId";
    private static final String INLINE_DELIVERY_MODE = "inline";

    @Reference
    private CerosManifestService cerosManifestService;

    @Override
    public void process(SlingHttpServletRequest request, List<Modification> changes) {
        Resource resource = request.getResource();
        if (resource == null || !resource.isResourceType(RESOURCE_TYPE)) {
            return;
        }

        // Only validate on a dialog save (the default modify operation); skip
        // delete/move/copy so they don't fail on manifest-URL validation.
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
        String experienceResourceId = null;
        if (urlMode && manifestUrl != null) {
            String canonical = resolveOrReject(manifestUrl);

            // The live modes fetch/inject server-side, so they must point at the
            // canonical Ceros host; embed and store keep the pasted URL (embed
            // loads it in an iframe; store resolves it via its own Fetch action).
            if ((inlineMode || fetchMode) && !canonical.equals(manifestUrl)) {
                props.put(PROP_MANIFEST_URL, canonical);
                changes.add(Modification.onModified(resource.getPath() + "/" + PROP_MANIFEST_URL));
            }

            CerosManifestV1 manifest = fetchManifest(canonical, inlineMode);

            // Inline mode also grabs the flex-client.js runtime URL to persist
            // for the render path.
            if (inlineMode) {
                scriptUrl = inlineScriptUrl(manifest, canonical);
            }
            experienceResourceId = experienceResourceId(manifest);
        }

        writeProperty(props, changes, resource, PROP_INLINE_SCRIPT_URL, scriptUrl);
        writeProperty(props, changes, resource, PROP_EXPERIENCE_RESOURCE_ID, experienceResourceId);
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
     * Fetches the (already trusted) manifest.
     *
     * <p>Inline mode needs the manifest to render at all — without the
     * {@code flex-client.js} URL the component is broken — so a fetch failure
     * there throws {@link IllegalArgumentException} and aborts the save, as it
     * always has. The other URL modes only want the experience ID out of it,
     * which is not worth failing a save over, so they get {@code null} and
     * carry on.</p>
     */
    private CerosManifestV1 fetchManifest(String canonical, boolean required) {
        try {
            return cerosManifestService.fetchPublicManifestFromUrl(canonical);
        } catch (IOException e) {
            log.warn("Could not fetch manifest from {}: {}", canonical, e.getMessage());
            if (required) {
                throw new IllegalArgumentException(CerosConstants.MSG_UNREACHABLE_EXPERIENCE);
            }
            return null;
        }
    }

    /**
     * Reads the experience's resource ID from the manifest. Returns {@code null}
     * when the manifest couldn't be fetched, or was published before it carried
     * the field — the property is then cleared rather than left stale.
     */
    private String experienceResourceId(CerosManifestV1 manifest) {
        CerosManifestV1.Experience experience = manifest != null ? manifest.getExperience() : null;
        return experience != null ? StringUtils.trimToNull(experience.getExperienceResourceId()) : null;
    }

    /**
     * Returns the manifest's inline {@code flex-client.js} URL, resolved against
     * the manifest URL — a no-op for the absolute URLs the live endpoint serves;
     * absolutises a relative URL from an exported manifest. Returns {@code null}
     * when the manifest exposes no inline script (nothing is persisted then).
     */
    private String inlineScriptUrl(CerosManifestV1 manifest, String canonical) {
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
     * Keeps a derived property in sync with the latest save: sets it when
     * present, clears a stale value otherwise (e.g. after switching away from
     * inline mode, or when the manifest no longer yields a value), recording a
     * modification only when it actually changes.
     */
    private void writeProperty(ModifiableValueMap props, List<Modification> changes,
                               Resource resource, String name, String value) {
        String existing = props.get(name, String.class);
        if (value != null) {
            if (!value.equals(existing)) {
                props.put(name, value);
                changes.add(Modification.onModified(resource.getPath() + "/" + name));
            }
        } else if (existing != null) {
            props.remove(name);
            changes.add(Modification.onModified(resource.getPath() + "/" + name));
        }
    }
}
