package com.ceros.config;

import org.apache.sling.caconfig.annotation.Configuration;
import org.apache.sling.caconfig.annotation.Property;

/**
 * Context-aware configuration controlling which Ceros Flex delivery modes are
 * offered in the {@code cerosflex} component dialog.
 *
 * <p>Resolved per content path via Sling Context-Aware Configuration (the site's
 * {@code sling:configRef}), so it is a single <strong>global</strong> setting
 * for a site — one value applies to every page under the config context,
 * regardless of template. Every mode defaults to {@code true}: with no stored
 * configuration all modes are available, and the configuration only ever
 * <em>restricts</em> the choices.</p>
 *
 * <p>Non-secret authoring policy only — no credentials belong here.</p>
 */
@Configuration(
        label = "Ceros Flex — Allowed Delivery Modes",
        description = "Which Ceros Flex delivery modes authors may choose in the component dialog, "
                + "for the whole site. All modes are enabled by default.")
public @interface CerosFlexModesConfig {

    @Property(label = "Server-side (Always Fetch)",
            description = "Live-fetch the manifest from the Ceros CDN on every render.")
    boolean fetch() default true;

    @Property(label = "Server-side (Store)",
            description = "Pre-fetch the manifest + assets into the DAM and serve offline.")
    boolean store() default true;

    @Property(label = "Server-side (HTML Import)",
            description = "Upload a Ceros .tar.gz export, unpacked into the DAM and served offline.")
    boolean htmlImport() default true;

    @Property(label = "Client-side (Inline embed)",
            description = "Render in the host page DOM via the Ceros inline marker + flex-client.js.")
    boolean inline() default true;

    @Property(label = "Client-side (Iframe embed)",
            description = "Render in an iframe via the Ceros embed script.")
    boolean embed() default true;
}
