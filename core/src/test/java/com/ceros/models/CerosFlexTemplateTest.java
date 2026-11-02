package com.ceros.models;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the cerosflex component's HTL template and site CSS in ui.apps. Neither
 * is rendered by the unit tests, so this guards the server-side wrapper contract
 * flex-ssr relies on (SPA-8224).
 */
class CerosFlexTemplateTest {

    // Surefire sets basedir to the core module, independent of the JVM's working directory.
    private static final Path COMPONENT = Paths.get(System.getProperty("basedir", "."))
            .resolve(Paths.get("..", "ui.apps", "src", "main", "content", "jcr_root",
                    "apps", "connectors", "ceros", "components", "cerosflex"))
            .normalize();

    /** Properties that, overridden on the wrapper or viewer, cancel flex-ssr's scale-mode sizing. */
    private static final Set<String> SIZING_PROPERTIES = Set.of(
            "height", "min-height", "max-height",
            "block-size", "min-block-size", "max-block-size",
            "contain", "display", "overflow", "overflow-y");

    private static String read(Path path) throws IOException {
        return Files.readString(COMPONENT.resolve(path));
    }

    @Test
    void ssrWrapper_optsIntoAutoHeightOnTheManifestUrlElement() throws IOException {
        String html = read(Paths.get("cerosflex.html"));
        Matcher wrapper = Pattern.compile(
                "<div\\b[^>]*\\bclass=\"[^\"]*\\bcmp-cerosflex__content\\b[^\"]*\"[^>]*>").matcher(html);

        assertTrue(wrapper.find(), "SSR content wrapper not found in cerosflex.html");
        String tag = wrapper.group();
        assertTrue(tag.contains("data-flex-manifest-url="), tag);
        assertTrue(tag.contains("data-flex-height=\"auto\""), tag);
        // Legacy name, read by flex-ssr releases pinned before the rename.
        assertTrue(tag.contains("data-embed-height=\"auto\""), tag);

        int branch = html.indexOf("!model.inlineMode && wcmmode.disabled}\">");
        int nextBranch = html.indexOf("model.embedMode && !wcmmode.disabled}\">");
        assertTrue(branch >= 0 && nextBranch > branch, "publish/preview SSR branch not found");
        assertTrue(wrapper.start() > branch && wrapper.start() < nextBranch,
                "SSR content wrapper must be inside the wcmmode.disabled SSR branch");
        assertFalse(wrapper.find(), "expected exactly one SSR content wrapper");
    }

    @Test
    void siteCss_onlyOverridesSizingForFluidExperiences() throws IOException {
        String css = read(Paths.get("clientlibs", "site", "css", "cerosflex.css"))
                .replaceAll("(?s)/\\*.*?\\*/", "");
        // Innermost rules only: a body never contains braces, so an @media
        // prelude is never mistaken for a selector.
        Matcher rule = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(css);

        int sizingRules = 0;
        while (rule.find()) {
            if (!overridesSizing(rule.group(2))) {
                continue;
            }
            sizingRules++;
            for (String selector : splitSelectors(rule.group(1))) {
                assertTrue(selector.contains("cml-experience-viewer--fluid"),
                        "sizing override must be scoped to fluid experiences: " + selector.trim());
            }
        }
        assertTrue(sizingRules > 0, "expected the fluid natural-height rule");
    }

    private static boolean overridesSizing(String declarations) {
        for (String declaration : declarations.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0 && SIZING_PROPERTIES.contains(
                    declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Splits a selector list on top-level commas, keeping {@code :is(a, b)} whole. */
    private static List<String> splitSelectors(String selectorList) {
        List<String> selectors = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < selectorList.length(); i++) {
            char c = selectorList.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                selectors.add(selectorList.substring(start, i));
                start = i + 1;
            }
        }
        selectors.add(selectorList.substring(start));
        return selectors;
    }
}
