package com.ceros.delivery;

import com.ceros.delivery.DeliveryResult.CssLink;
import com.ceros.delivery.DeliveryResult.ScriptRef;
import com.ceros.models.cerosflex.CerosManifestV1;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a parsed {@link CerosManifestV1} into the view state HTL needs.
 * Shared by the fetch and store delivery handlers so both produce identical
 * output for the same input manifest.
 */
public final class ManifestRenderer {

    private ManifestRenderer() {
        // static utility
    }

    /**
     * Populates the result builder with HTML, CSS, scripts, and the embed
     * script URL derived from {@code manifest}.
     */
    public static void renderInto(DeliveryResult.Builder builder,
                                  CerosManifestV1 manifest) {
        if (manifest == null) {
            return;
        }

        String html = DeliveryResult.preserveAnchorsFromLinkChecker(manifest.getHtmlBodyContent());
        builder.htmlContent(html);

        List<CssLink> css = new ArrayList<>();
        List<ScriptRef> bodyScripts = new ArrayList<>();
        CerosManifestV1.DeliveryMode ssr = manifest.getDeliveryMode("ssr");
        if (ssr != null) {
            for (CerosManifestV1.Style style : ssr.getStyles()) {
                if (style.getUrl() != null) {
                    css.add(new CssLink(style.getUrl(), style.getIntegrity()));
                }
            }
            for (CerosManifestV1.Script script : ssr.getScripts()) {
                if (script.getUrl() != null) {
                    bodyScripts.add(new ScriptRef(script));
                }
            }
        }

        // Web fonts are prepended so they preload before any SSR style.
        List<CssLink> fonts = new ArrayList<>();
        for (CerosManifestV1.AssetEntry entry : manifest.getAssets()) {
            if ("webfont".equals(entry.getType())
                    && entry.getSrc() != null
                    && entry.getSrc().getUrl() != null) {
                fonts.add(new CssLink(entry.getSrc().getUrl(), entry.getSrc().getIntegrity()));
            }
        }
        if (!fonts.isEmpty()) {
            fonts.addAll(css);
            css = fonts;
        }
        builder.cssLinks(css);
        builder.bodyScripts(bodyScripts);

        List<ScriptRef> headScripts = new ArrayList<>();
        for (CerosManifestV1.AssetEntry entry : manifest.getAssets()) {
            if ("script".equals(entry.getType())) {
                headScripts.add(new ScriptRef(entry));
            }
        }
        builder.headScripts(headScripts);

        CerosManifestV1.DeliveryMode iframe = manifest.getDeliveryMode("iframe");
        if (iframe != null && !iframe.getScripts().isEmpty()) {
            builder.embedScriptUrl(iframe.getScripts().get(0).getUrl());
        }

        builder.hasContent(html != null
                || ssr != null
                || !headScripts.isEmpty()
                || !bodyScripts.isEmpty()
                || builder.build().getEmbedScriptUrl() != null);
    }
}
