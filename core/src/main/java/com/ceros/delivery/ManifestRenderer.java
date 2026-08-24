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

    /**
     * Basename of the Flex Experience SDK module on the Ceros CDN. It sits
     * alongside the SSR runtime script, and flex-cdn does not version-stamp
     * these filenames — the CDN path segment is immutable per deploy, so the
     * base URL already cache-busts. That is what makes the SDK URL derivable
     * from the SSR script's URL rather than needing to be in the manifest.
     */
    private static final String FLEX_SDK_MODULE_FILE = "flex-experience-sdk.js";

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
        if (display != null) {
            builder.customBodyHtml(display.getCustomBodyHtml());
        }

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

        builder.sdkModuleUrl(deriveSdkModuleUrl(ssr));

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
     * The Flex Experience SDK module's URL, derived by swapping the basename of
     * an SSR runtime script's URL for {@link #FLEX_SDK_MODULE_FILE}. Null when
     * the manifest has no SSR script to derive from.
     *
     * <p>The manifest carries no import map and no SDK URL of its own — Ceros
     * renders an import map only on the standalone page, never for SSR — so
     * deriving is the only way the connector can resolve the SDK's bare
     * specifier for injected custom body HTML. It assumes the SDK stays a
     * sibling of the SSR script; if that changes, the specifier stops
     * resolving and the browser reports it.</p>
     */
    private static String deriveSdkModuleUrl(CerosManifestV1.DeliveryMode ssr) {
        if (ssr == null) {
            return null;
        }
        for (CerosManifestV1.Script script : ssr.getScripts()) {
            String url = script.getUrl();
            if (url == null) {
                continue;
            }
            // Query and fragment sit after the basename, so drop them first.
            int cut = url.length();
            for (char c : new char[] {'?', '#'}) {
                int i = url.indexOf(c);
                if (i >= 0 && i < cut) {
                    cut = i;
                }
            }
            String path = url.substring(0, cut);
            int slash = path.lastIndexOf('/');
            if (slash < 0) {
                continue;
            }
            return path.substring(0, slash + 1) + FLEX_SDK_MODULE_FILE;
        }
        return null;
    }
}
