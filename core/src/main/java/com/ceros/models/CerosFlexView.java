package com.ceros.models;

import javax.annotation.PostConstruct;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;

import com.ceros.delivery.DeliveryResult;
import com.ceros.services.CerosFlexDeliveryService;

import lombok.experimental.Delegate;

/**
 * Sling Model HTL binds to. Wraps the {@link CerosFlexModel} data POJO with
 * the rendered {@link DeliveryResult} produced by {@link CerosFlexDeliveryService}.
 */
@Model(adaptables = SlingHttpServletRequest.class,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class CerosFlexView {

    private interface ExcludedMethods {
        String getInlineScriptUrl(); 
    }

    @Self
    @Delegate
    private CerosFlexModel model;

    @Self
    private SlingHttpServletRequest request;

    @SlingObject
    private Resource resource;

    @OSGiService
    private CerosFlexDeliveryService deliveryService;

    @Delegate(excludes=ExcludedMethods.class)
    private DeliveryResult result = DeliveryResult.EMPTY;

    @PostConstruct
    protected void init() {
        if (deliveryService != null) {
            result = deliveryService.deliver(model, request, resource);
        }
    }

    // ---- Delivery-result getters ----

    public String getManifestUrl() {
        return result.getManifestUrl() != null ? result.getManifestUrl()
                : (model != null ? model.getManifestUrl() : null);
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
