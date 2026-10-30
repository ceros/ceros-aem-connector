package com.ceros.services.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CerosFlexDeliveryServiceImplTest {

    @Test
    void analyticsIsOffBeforeActivation() {
        assertFalse(new CerosFlexDeliveryServiceImpl().isAnalyticsEnabled());
    }

    @Test
    void analyticsFollowsTheConfig() {
        CerosFlexDeliveryServiceImpl service = new CerosFlexDeliveryServiceImpl();
        CerosFlexDeliveryServiceImpl.Config config = mock(CerosFlexDeliveryServiceImpl.Config.class);

        when(config.analyticsEnabled()).thenReturn(false);
        service.activate(config);
        assertFalse(service.isAnalyticsEnabled());

        when(config.analyticsEnabled()).thenReturn(true);
        service.activate(config);
        assertTrue(service.isAnalyticsEnabled());
    }
}
