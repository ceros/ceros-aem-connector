package com.ceros.services.impl;

import com.ceros.delivery.DeliveryResult;
import com.ceros.delivery.modes.DeliveryHandler;
import com.ceros.models.CerosFlexModel;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.CerosFlexDeliveryService;
import com.ceros.services.CerosManifestService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(service = CerosFlexDeliveryService.class)
@Designate(ocd = CerosFlexDeliveryServiceImpl.Config.class)
public class CerosFlexDeliveryServiceImpl implements CerosFlexDeliveryService {

    @ObjectClassDefinition(name = "Ceros Flex Delivery Service",
            description = "Renders Ceros Flex components in their configured delivery mode")
    @interface Config {
        @AttributeDefinition(name = "Ceros analytics",
                description = "Let Ceros Flex experiences on this instance report to Ceros Analytics. "
                        + "Off unless configured; the connector turns it on for publish, but not "
                        + "for preview. Turn it off for any other publish environment that real "
                        + "visitors don't use, such as stage.")
        boolean analyticsEnabled() default false;
    }

    @Reference
    private CerosManifestService manifestService;

    @Reference
    private CerosAssetStorageService assetStorageService;

    private boolean analyticsEnabled;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.analyticsEnabled = config.analyticsEnabled();
    }

    @Override
    public DeliveryResult deliver(CerosFlexModel model,
                                  SlingHttpServletRequest request,
                                  Resource resource) {
        if (model == null || !model.isConfigured()) {
            return DeliveryResult.EMPTY;
        }
        DeliveryHandler.DeliveryContext ctx = new DeliveryHandler.DeliveryContext(
                model.getManifestUrl(),
                model.getCerosPrefetchedManifestJson(),
                model.getInlineScriptUrl(),
                request,
                resource);
        return DeliveryHandler.forMode(model.getCerosMode(), manifestService, assetStorageService).handle(ctx);
    }

    @Override
    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }
}
