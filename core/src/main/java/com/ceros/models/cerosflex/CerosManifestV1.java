package com.ceros.models.cerosflex;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CerosManifestV1 {

    static final ObjectMapper MAPPER = new ObjectMapper();

    String schemaVersion;
    String publishedAt;
    Experience experience;
    PageMetadata pageMetadata;
    DisplayMetadata displayMetadata;
    final Map<String, DeliveryMode> deliveryModes = Collections.emptyMap();
    final List<AssetEntry> assets = Collections.emptyList();
    final List<MediaEntry> media = Collections.emptyList();
    final List<PageRef> pages = Collections.emptyList();


    public DeliveryMode getDeliveryMode(String mode) {
        return deliveryModes != null ? deliveryModes.get(mode) : null;
    }

    public String getHtmlBodyContent() {
        AssetEntry entry = getHtmlBodyAsset();
        return entry != null && entry.getSrc() != null ? entry.getSrc().getContent() : null;
    }

    public AssetEntry getHtmlBodyAsset() {
        if (assets == null) return null;
        for (AssetEntry entry : assets) {
            if ("html-body".equals(entry.getType())) {
                return entry;
            }
        }
        return null;
    }

    public static CerosManifestV1 parseManifest(String json) throws IOException {
        return MAPPER.readValue(json, CerosManifestV1.class);
    }

    // ---- Experience ----

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        String slug;
        String accountSlug;
        String pageSlug;
        int pageNumber;
    }

    // ---- PageMetadata ----

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMetadata {
        String title;
        String description;
        List<String> keywords;
        String canonicalUrl;
        String robots;
        String locale;
        Favicon favicon;
        OpenGraph openGraph;
        Twitter twitter;
        List<CustomMetaTag> customMetaTags;
        String customHeadHtml;
        String noScriptHtml;
        String seoMode;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Favicon {
        String url;
        String mimeType;
        String sizes;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenGraph {
        String title;
        String description;
        String type;
        String url;
        String locale;
        String siteName;
        OpenGraphImage image;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenGraphImage {
        String url;
        String mimeType;
        Integer width;
        Integer height;
        String alt;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Twitter {
        String card;
        String title;
        String description;
        String site;
        String creator;
        TwitterImage image;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwitterImage {
        String url;
        String alt;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomMetaTag {
        String name;
        String property;
        String httpEquiv;
        String content;
    }

    // ---- DisplayMetadata ----

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DisplayMetadata {
        String mode;
        DesignViewport designViewport;
        String customBodyHtml;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DesignViewport {
        String width;
        String height;
    }

    // ---- DeliveryMode ----

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeliveryMode {
        String description;
        String snippet;
        List<Script> scripts = Collections.emptyList();
        List<Style> styles = Collections.emptyList();

        public void setStyles(List<Style> styles) { this.styles = List.copyOf(styles); }
        public void setScripts(List<Script> scripts) { this.scripts = List.copyOf(scripts); }
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Script {
        @Setter String url;
        String mimeType;
        long size;
        String integrity;
        String loadStrategy;
        boolean module;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Style {
        @Setter String url;
        @Setter String mimeType;
        long size;
        String integrity;
    }

    // ---- AssetEntry ----

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetEntry {
        String type;
        String name;
        String description;
        boolean optional;
        AssetSource src;
        AssetMetadata metadata;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetSource {
        String type;
        @Setter String url;
        @Setter String content;
        String mimeType;
        long size;
        String integrity;
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetMetadata {
        String purpose;
        String loadStrategy;
        Boolean module;
        String media;
        Boolean preload;
    }

    // ---- MediaEntry ----

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaEntry {
        String type;
        String name;
        String url;
        String mimeType;
        String filename;
        String alt;
        String posterUrl;
    }

    // ---- PageRef ----

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageRef {
        String slug;
        String label;
        @Setter String manifestUrl;
        boolean isFirst;
        boolean current;
    }
}
