package com.ceros.services;

/**
 * Feature toggles for Ceros Flex component rendering.
 */
public interface CerosFlexFeatureConfig {

    /**
     * @return true when authored Full Height/Scrolling choices for inline
     *         embed mode take effect at render time
     */
    boolean isInlineHeightControlEnabled();
}
