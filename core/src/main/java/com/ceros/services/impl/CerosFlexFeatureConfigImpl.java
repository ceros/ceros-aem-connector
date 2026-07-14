package com.ceros.services.impl;

import com.ceros.services.CerosFlexFeatureConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi implementation of {@link CerosFlexFeatureConfig}.
 *
 * <p>The inline-height toggle is primarily a kill switch: disabling it stops
 * {@code data-flex-height} emission from every inline component at render
 * time (no code rollback needed), while dialog fields stay authorable.
 */
@Component(service = CerosFlexFeatureConfig.class)
@Designate(ocd = CerosFlexFeatureConfigImpl.Config.class)
public class CerosFlexFeatureConfigImpl implements CerosFlexFeatureConfig {

    @ObjectClassDefinition(name = "Ceros Flex Feature Config",
            description = "Feature toggles for Ceros Flex component rendering")
    @interface Config {
        @AttributeDefinition(name = "Enable inline-mode height control",
                description = "When enabled, authored Full Height/Scrolling choices for inline "
                        + "embed mode take effect at render time. When disabled, inline mode "
                        + "always renders with today's default (content-sized, no imposed height).")
        boolean inlineHeightControlEnabled() default true;
    }

    private boolean inlineHeightControlEnabled;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.inlineHeightControlEnabled = config.inlineHeightControlEnabled();
    }

    @Override
    public boolean isInlineHeightControlEnabled() {
        return inlineHeightControlEnabled;
    }
}
