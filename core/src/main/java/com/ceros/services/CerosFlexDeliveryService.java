package com.ceros.services;

import com.ceros.delivery.DeliveryResult;
import com.ceros.models.CerosFlexModel;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;

/**
 * Produces a {@link DeliveryResult} from a {@link CerosFlexModel} by dispatching
 * to the appropriate delivery handler for the model's mode.
 */
public interface CerosFlexDeliveryService {

    DeliveryResult deliver(CerosFlexModel model,
                           SlingHttpServletRequest request,
                           Resource resource);

    /**
     * Whether this instance lets Ceros Flex experiences report to Ceros
     * Analytics at all. Off on author and preview, where every visitor is an
     * author checking their own page. When off, no component reports, whatever
     * its own setting says.
     */
    default boolean isAnalyticsEnabled() {
        return true;
    }
}
