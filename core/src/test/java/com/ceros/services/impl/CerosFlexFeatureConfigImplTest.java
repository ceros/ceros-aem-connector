package com.ceros.services.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CerosFlexFeatureConfigImplTest {

    private CerosFlexFeatureConfigImpl activated(boolean enabled) {
        CerosFlexFeatureConfigImpl.Config config = mock(CerosFlexFeatureConfigImpl.Config.class);
        when(config.inlineHeightControlEnabled()).thenReturn(enabled);
        CerosFlexFeatureConfigImpl impl = new CerosFlexFeatureConfigImpl();
        impl.activate(config);
        return impl;
    }

    @Test
    void activateWiresEnabledThrough() {
        assertTrue(activated(true).isInlineHeightControlEnabled());
    }

    @Test
    void activateWiresDisabledThrough() {
        assertFalse(activated(false).isInlineHeightControlEnabled());
    }
}
