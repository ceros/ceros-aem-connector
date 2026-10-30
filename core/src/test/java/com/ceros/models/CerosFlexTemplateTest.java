package com.ceros.models;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the cerosflex component's HTL template and site CSS in ui.apps. Neither
 * is rendered by the unit tests, so this guards the server-side wrapper contract
 * flex-ssr relies on (SPA-8224).
 */
class CerosFlexTemplateTest {

    private static final Path COMPONENT = Paths.get(
            "..", "ui.apps", "src", "main", "content", "jcr_root",
            "apps", "connectors", "ceros", "components", "cerosflex");

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(COMPONENT.resolve(path)), StandardCharsets.UTF_8);
    }

    @Test
    void ssrWrapper_optsIntoAutoHeightOnTheManifestUrlElement() throws IOException {
        String html = read(Paths.get("cerosflex.html"));
        Matcher wrapper = Pattern.compile("<div class=\"cmp-cerosflex__content\"[^>]*>").matcher(html);

        assertTrue(wrapper.find(), "SSR content wrapper not found in cerosflex.html");
        String tag = wrapper.group();
        assertTrue(tag.contains("data-flex-manifest-url="), tag);
        assertTrue(tag.contains("data-flex-height=\"auto\""), tag);
        assertFalse(tag.contains("data-embed-height"), tag);
        assertFalse(wrapper.find(), "expected exactly one SSR content wrapper");
    }

    @Test
    void siteCss_onlyForcesHeightForFluidExperiences() throws IOException {
        String css = read(Paths.get("clientlibs", "site", "css", "cerosflex.css"))
                .replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher rule = Pattern.compile("([^{}]+)\\{([^}]*)\\}").matcher(css);

        int heightRules = 0;
        while (rule.find()) {
            if (!rule.group(2).contains("height")) {
                continue;
            }
            heightRules++;
            for (String selector : rule.group(1).split(",")) {
                assertTrue(selector.contains("cml-experience-viewer--fluid"),
                        "height override must be scoped to fluid experiences: " + selector.trim());
            }
        }
        assertTrue(heightRules > 0, "expected the fluid natural-height rule");
    }
}
