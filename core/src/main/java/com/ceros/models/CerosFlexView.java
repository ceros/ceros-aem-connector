package com.ceros.models;

import com.ceros.delivery.DeliveryResult;
import com.ceros.services.CerosFlexDeliveryService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Sling Model HTL binds to. Wraps the {@link CerosFlexModel} data POJO with
 * the rendered {@link DeliveryResult} produced by {@link CerosFlexDeliveryService}.
 */
@Model(adaptables = SlingHttpServletRequest.class,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class CerosFlexView {

    @Self
    private CerosFlexModel model;

    @Self
    private SlingHttpServletRequest request;

    @SlingObject
    private Resource resource;

    @OSGiService
    private CerosFlexDeliveryService deliveryService;

    private DeliveryResult result = DeliveryResult.EMPTY;

    @PostConstruct
    protected void init() {
        if (deliveryService != null) {
            result = deliveryService.deliver(model, request, resource);
        }
    }

    // ---- Delegated data getters ----

    public boolean isConfigured() {
        return model != null && model.isConfigured();
    }

    public boolean isStoreMode() {
        return model != null && model.isStoreMode();
    }

    public boolean isEmbedMode() {
        return model != null && model.isEmbedMode();
    }

    public boolean isInlineMode() {
        return model != null && model.isInlineMode();
    }

    public String getPrefetchedAt() {
        return model != null ? model.getPrefetchedAt() : null;
    }

    public String getPagePreviewUrl() {
        return model != null ? model.getPagePreviewUrl() : null;
    }

    // ---- Delivery-result getters ----

    public String getManifestUrl() {
        return result.getManifestUrl() != null ? result.getManifestUrl()
                : (model != null ? model.getManifestUrl() : null);
    }

    public String getExperienceUrl() {
        return result.getExperienceUrl();
    }

    public boolean isHasContent() {
        return result.isHasContent();
    }

    public String getHtmlContent() {
        return result.getHtmlContent();
    }

    public List<DeliveryResult.CssLink> getCssLinks() {
        return result.getCssLinks();
    }

    public List<DeliveryResult.ScriptRef> getHeadScripts() {
        return result.getHeadScripts();
    }

    public List<DeliveryResult.ScriptRef> getBodyScripts() {
        return result.getBodyScripts();
    }

    public String getEmbedTitle() {
        return result.getEmbedTitle();
    }

    public String getEmbedScriptUrl() {
        return result.getEmbedScriptUrl();
    }

    /** {@code flex-client.js} URL for the client-side inline embed snippet. */
    public String getInlineScriptUrl() {
        return result.getInlineScriptUrl();
    }

    /** Authored {@code data-embed-height} value for the iframe-embed snippet. */
    public String getEmbedHeightAttribute() {
        return model != null ? model.getEmbedHeightAttribute() : "auto";
    }

    /**
     * URL used as the iframe src for the author-mode store preview. Resolves to
     * {@code cerosflex.preview.html} on the same component — a minimal page
     * containing only the experience (CSS + HTML + SSR scripts) and no AEM
     * chrome. The SPA router runs inside the iframe, isolated from the editor.
     */
    public String getPreviewPageUrl() {
        if (resource == null) {
            return null;
        }
        return resource.getPath() + ".preview.html";
    }
}
