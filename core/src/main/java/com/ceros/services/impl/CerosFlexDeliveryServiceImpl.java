package com.ceros.services.impl;

import com.ceros.delivery.DeliveryResult;
import com.ceros.delivery.modes.DeliveryHandler;
import com.ceros.models.CerosFlexModel;
import com.ceros.services.CerosAssetStorageService;
import com.ceros.services.CerosFlexDeliveryService;
import com.ceros.services.CerosManifestService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = CerosFlexDeliveryService.class)
public class CerosFlexDeliveryServiceImpl implements CerosFlexDeliveryService {

    @Reference
    private CerosManifestService manifestService;

    @Reference
    private CerosAssetStorageService assetStorageService;

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
                request,
                resource);
        return DeliveryHandler.forMode(model.getCerosMode(), manifestService, assetStorageService).handle(ctx);
    }
}
