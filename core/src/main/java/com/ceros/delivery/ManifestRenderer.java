package com.ceros.delivery;

import com.ceros.delivery.DeliveryResult.CssLink;
import com.ceros.delivery.DeliveryResult.ScriptRef;
import com.ceros.models.cerosflex.CerosManifestV1;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
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

        // The author's custom Body HTML rides in displayMetadata, not assets[].
        // Always carried on the result; CerosFlexView decides whether to emit it.
        CerosManifestV1.DisplayMetadata display = manifest.getDisplayMetadata();
        String customBodyHtml = display != null ? display.getCustomBodyHtml() : null;
        builder.customBodyHtml(customBodyHtml);
        builder.importMapJson(importMapJsonFor(manifest, customBodyHtml));

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

        // type="style" assets carry the brand kit — the design tokens (colours)
        // and text-style rules (fonts) the experience needs to render correctly.
        List<String> inlineStyles = new ArrayList<>();
        for (CerosManifestV1.AssetEntry entry : manifest.getAssets()) {
            if (!"style".equals(entry.getType()) || entry.getSrc() == null) {
                continue;
            }
            CerosManifestV1.AssetSource src = entry.getSrc();
            if (src.getUrl() != null) {
                css.add(new CssLink(src.getUrl(), src.getIntegrity()));
            } else if (src.getContent() != null && !src.getContent().isEmpty()) {
                inlineStyles.add(src.getContent());
            }
        }

        builder.cssLinks(css);
        builder.inlineStyles(inlineStyles);
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

    /**
     * The experience's import map, serialised for an inline
     * {@code <script type="importmap">}, or null when the page needs none.
     *
     * <p>Emitted verbatim from the manifest so the {@code integrity} section
     * rides along and the SDK module keeps its SRI. Ceros renders an import map
     * only on the standalone published page — flex-player's
     * {@code getImportMap} documents that SSR deliveries get none — so without
     * this a module script in the injected custom body HTML cannot resolve the
     * specifiers it imports by name.</p>
     *
     * <p>Emitted only when the custom body HTML actually imports one of the
     * map's specifiers. A document may carry a single import map, so an
     * experience that needs none stays out of the way of any the host AEM page
     * defines for itself.</p>
     */
    private static String importMapJsonFor(CerosManifestV1 manifest, String customBodyHtml) {
        JsonNode importMap = manifest.getImportMap();
        if (customBodyHtml == null || importMap == null || !importMap.isObject()) {
            return null;
        }
        JsonNode imports = importMap.get("imports");
        if (imports == null || !imports.isObject()) {
            return null;
        }

        boolean used = false;
        for (Iterator<String> it = imports.fieldNames(); it.hasNext(); ) {
            if (customBodyHtml.contains(it.next())) {
                used = true;
                break;
            }
        }
        if (!used) {
            return null;
        }

        // Escape "<" so no value in the map can close the script element it is
        // interpolated into ("</script>", "<!--"). \u003c is valid JSON and
        // parses back to "<", so the map itself is unchanged.
        return importMap.toString().replace("<", "\\u003c");
    }
}
