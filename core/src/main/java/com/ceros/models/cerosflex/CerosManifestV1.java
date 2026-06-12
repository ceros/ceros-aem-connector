package com.ceros.models.cerosflex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CerosManifestV1 {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonProperty("schemaVersion")
    private String schemaVersion;

    @JsonProperty("publishedAt")
    private String publishedAt;

    @JsonProperty("experience")
    private Experience experience;

    @JsonProperty("pageMetadata")
    private PageMetadata pageMetadata;

    @JsonProperty("displayMetadata")
    private DisplayMetadata displayMetadata;

    @JsonProperty("deliveryModes")
    private Map<String, DeliveryMode> deliveryModes;

    @JsonProperty("assets")
    private List<AssetEntry> assets;

    @JsonProperty("media")
    private List<MediaEntry> media;

    @JsonProperty("pages")
    private List<PageRef> pages;

    public String getSchemaVersion() { return schemaVersion; }
    public String getPublishedAt() { return publishedAt; }
    public Experience getExperience() { return experience; }
    public PageMetadata getPageMetadata() { return pageMetadata; }
    public DisplayMetadata getDisplayMetadata() { return displayMetadata; }
    public List<AssetEntry> getAssets() { return assets != null ? assets : Collections.emptyList(); }
    public List<MediaEntry> getMedia() { return media != null ? media : Collections.emptyList(); }
    public List<PageRef> getPages() { return pages != null ? pages : Collections.emptyList(); }

    public Map<String, DeliveryMode> getDeliveryModes() {
        return deliveryModes != null ? deliveryModes : Collections.emptyMap();
    }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        @JsonProperty("slug")
        private String slug;

        @JsonProperty("accountSlug")
        private String accountSlug;

        @JsonProperty("pageSlug")
        private String pageSlug;

        @JsonProperty("pageNumber")
        private int pageNumber;

        public String getSlug() { return slug; }
        public String getAccountSlug() { return accountSlug; }
        public String getPageSlug() { return pageSlug; }
        public int getPageNumber() { return pageNumber; }
    }

    // ---- PageMetadata ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMetadata {
        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("keywords")
        private List<String> keywords;

        @JsonProperty("canonicalUrl")
        private String canonicalUrl;

        @JsonProperty("robots")
        private String robots;

        @JsonProperty("locale")
        private String locale;

        @JsonProperty("favicon")
        private Favicon favicon;

        @JsonProperty("openGraph")
        private OpenGraph openGraph;

        @JsonProperty("twitter")
        private Twitter twitter;

        @JsonProperty("customMetaTags")
        private List<CustomMetaTag> customMetaTags;

        @JsonProperty("customHeadHtml")
        private String customHeadHtml;

        @JsonProperty("noScriptHtml")
        private String noScriptHtml;

        @JsonProperty("seoMode")
        private String seoMode;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public List<String> getKeywords() { return keywords; }
        public String getCanonicalUrl() { return canonicalUrl; }
        public String getRobots() { return robots; }
        public String getLocale() { return locale; }
        public Favicon getFavicon() { return favicon; }
        public OpenGraph getOpenGraph() { return openGraph; }
        public Twitter getTwitter() { return twitter; }
        public List<CustomMetaTag> getCustomMetaTags() { return customMetaTags; }
        public String getCustomHeadHtml() { return customHeadHtml; }
        public String getNoScriptHtml() { return noScriptHtml; }
        public String getSeoMode() { return seoMode; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Favicon {
        @JsonProperty("url")
        private String url;

        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("sizes")
        private String sizes;

        public String getUrl() { return url; }
        public String getMimeType() { return mimeType; }
        public String getSizes() { return sizes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenGraph {
        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        @JsonProperty("locale")
        private String locale;

        @JsonProperty("siteName")
        private String siteName;

        @JsonProperty("image")
        private OpenGraphImage image;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getType() { return type; }
        public String getUrl() { return url; }
        public String getLocale() { return locale; }
        public String getSiteName() { return siteName; }
        public OpenGraphImage getImage() { return image; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenGraphImage {
        @JsonProperty("url")
        private String url;

        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("width")
        private Integer width;

        @JsonProperty("height")
        private Integer height;

        @JsonProperty("alt")
        private String alt;

        public String getUrl() { return url; }
        public String getMimeType() { return mimeType; }
        public Integer getWidth() { return width; }
        public Integer getHeight() { return height; }
        public String getAlt() { return alt; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Twitter {
        @JsonProperty("card")
        private String card;

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("site")
        private String site;

        @JsonProperty("creator")
        private String creator;

        @JsonProperty("image")
        private TwitterImage image;

        public String getCard() { return card; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getSite() { return site; }
        public String getCreator() { return creator; }
        public TwitterImage getImage() { return image; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwitterImage {
        @JsonProperty("url")
        private String url;

        @JsonProperty("alt")
        private String alt;

        public String getUrl() { return url; }
        public String getAlt() { return alt; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomMetaTag {
        @JsonProperty("name")
        private String name;

        @JsonProperty("property")
        private String property;

        @JsonProperty("httpEquiv")
        private String httpEquiv;

        @JsonProperty("content")
        private String content;

        public String getName() { return name; }
        public String getProperty() { return property; }
        public String getHttpEquiv() { return httpEquiv; }
        public String getContent() { return content; }
    }

    // ---- DisplayMetadata ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DisplayMetadata {
        @JsonProperty("mode")
        private String mode;

        @JsonProperty("designViewport")
        private DesignViewport designViewport;

        @JsonProperty("customBodyHtml")
        private String customBodyHtml;

        public String getMode() { return mode; }
        public DesignViewport getDesignViewport() { return designViewport; }
        public String getCustomBodyHtml() { return customBodyHtml; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DesignViewport {
        @JsonProperty("width")
        private Object width;

        @JsonProperty("height")
        private Object height;

        public String getWidth() { return width != null ? String.valueOf(width) : null; }
        public String getHeight() { return height != null ? String.valueOf(height) : null; }
    }

    // ---- DeliveryMode ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeliveryMode {
        @JsonProperty("description")
        private String description;

        @JsonProperty("snippet")
        private String snippet;

        @JsonProperty("scripts")
        private List<Script> scripts;

        @JsonProperty("styles")
        private List<Style> styles;

        public String getDescription() { return description; }
        public String getSnippet() { return snippet; }
        public List<Script> getScripts() { return scripts != null ? scripts : Collections.emptyList(); }
        public List<Style> getStyles() { return styles != null ? styles : Collections.emptyList(); }

        public void setStyles(List<Style> styles) { this.styles = styles; }
        public void setScripts(List<Script> scripts) { this.scripts = scripts; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Script {
        @JsonProperty("url")
        private String url;

        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("size")
        private long size;

        @JsonProperty("integrity")
        private String integrity;

        @JsonProperty("loadStrategy")
        private String loadStrategy;

        @JsonProperty("module")
        private boolean module;

        public String getUrl() { return url; }
        public String getMimeType() { return mimeType; }
        public long getSize() { return size; }
        public String getIntegrity() { return integrity; }
        public String getLoadStrategy() { return loadStrategy; }
        public boolean isModule() { return module; }

        public void setUrl(String url) { this.url = url; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Style {
        @JsonProperty("url")
        private String url;

        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("size")
        private long size;

        @JsonProperty("integrity")
        private String integrity;

        public String getUrl() { return url; }
        public String getMimeType() { return mimeType; }
        public long getSize() { return size; }
        public String getIntegrity() { return integrity; }

        public void setUrl(String url) { this.url = url; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    }

    // ---- AssetEntry ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetEntry {
        @JsonProperty("type")
        private String type;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("optional")
        private boolean optional;

        @JsonProperty("src")
        private AssetSource src;

        @JsonProperty("metadata")
        private AssetMetadata metadata;

        public String getType() { return type; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isOptional() { return optional; }
        public AssetSource getSrc() { return src; }
        public AssetMetadata getMetadata() { return metadata; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetSource {
        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        @JsonProperty("content")
        private String content;

        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("size")
        private long size;

        @JsonProperty("integrity")
        private String integrity;

        public String getType() { return type; }
        public String getUrl() { return url; }
        public String getContent() { return content; }
        public String getMimeType() { return mimeType; }
        public long getSize() { return size; }
        public String getIntegrity() { return integrity; }

        public void setContent(String content) { this.content = content; }
        public void setUrl(String url) { this.url = url; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetMetadata {
        @JsonProperty("purpose")
        private String purpose;

        @JsonProperty("loadStrategy")
        private String loadStrategy;

        @JsonProperty("module")
        private Boolean module;

        @JsonProperty("media")
        private String media;

        @JsonProperty("preload")
        private Boolean preload;

        public String getPurpose() { return purpose; }
        public String getLoadStrategy() { return loadStrategy; }
        public Boolean getModule() { return module; }
        public String getMedia() { return media; }
        public Boolean getPreload() { return preload; }
    }

    // ---- MediaEntry ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaEntry {
        @JsonProperty("type")
        private String type;

        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private String url;

        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("filename")
        private String filename;

        @JsonProperty("alt")
        private String alt;

        @JsonProperty("posterUrl")
        private String posterUrl;

        public String getType() { return type; }
        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getMimeType() { return mimeType; }
        public String getFilename() { return filename; }
        public String getAlt() { return alt; }
        public String getPosterUrl() { return posterUrl; }
    }

    // ---- PageRef ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageRef {
        @JsonProperty("slug")
        private String slug;

        @JsonProperty("label")
        private String label;

        @JsonProperty("manifestUrl")
        private String manifestUrl;

        @JsonProperty("isFirst")
        private boolean isFirst;

        @JsonProperty("current")
        private boolean current;

        public String getSlug() { return slug; }
        public String getLabel() { return label; }
        public String getManifestUrl() { return manifestUrl; }
        public boolean isFirst() { return isFirst; }
        public boolean isCurrent() { return current; }

        public void setManifestUrl(String manifestUrl) { this.manifestUrl = manifestUrl; }
    }
}
