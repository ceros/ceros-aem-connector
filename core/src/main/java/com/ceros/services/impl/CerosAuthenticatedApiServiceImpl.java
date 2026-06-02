package com.ceros.services.impl;

import com.ceros.CerosConstants;
import com.ceros.services.CerosAuthenticatedApiService;
import com.ceros.util.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.io.IOException;
import java.util.Map;

/**
 * OSGi implementation of {@link CerosAuthenticatedApiService}.
 *
 * <p>Authenticates to the Ceros Flex REST API with a Bearer token and
 * provides a folder-tree of published experiences for the authoring dialog.
 * The service is disabled when no API key is configured.</p>
 */
@Component(service = CerosAuthenticatedApiService.class)
@Designate(ocd = CerosAuthenticatedApiServiceImpl.Config.class)
public class CerosAuthenticatedApiServiceImpl implements CerosAuthenticatedApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ObjectClassDefinition(name = "Ceros Flex API Service",
            description = "Connects to the Ceros Flex REST API for experience browsing")
    @interface Config {
        @AttributeDefinition(name = "Flex API Key",
                description = "Bearer token for the Ceros Flex API. Leave empty to disable browse mode.")
        String flexApiKey() default "";

        @AttributeDefinition(name = "API Base URL",
                description = "Base URL for the Ceros Flex REST API")
        String flexApiBaseUrl() default "https://rest.ceros.com";

        @AttributeDefinition(name = "View Base URL",
                description = "Base domain for manifest URLs. Account slug is prepended as subdomain (e.g. https://view.ceros.com)")
        String flexViewBaseUrl() default "https://ceros.site";

        @AttributeDefinition(name = "HTTP Timeout (seconds)")
        int httpTimeoutSeconds() default 30;
    }

    private String apiKey;
    private String apiBaseUrl;
    private String viewBaseUrl;
    private int httpTimeoutMillis;
    private volatile String cachedAccountResourceId;
    private volatile String cachedAccountName;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.apiKey = StringUtils.trimToNull(config.flexApiKey());
        this.apiBaseUrl = StringUtils.stripEnd(config.flexApiBaseUrl(), "/");
        this.viewBaseUrl = StringUtils.stripEnd(config.flexViewBaseUrl(), "/");
        this.httpTimeoutMillis = config.httpTimeoutSeconds() * 1000;
        this.cachedAccountResourceId = null;
        this.cachedAccountName = null;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null;
    }

    @Override
    public String getFolderTreeJson() throws IOException {
        if (!isEnabled()) {
            throw new IOException("Flex API key is not configured");
        }

        // Resolve account name and ID
        String accountId = resolveAccountId();
        String accountName = cachedAccountName != null ? cachedAccountName : "";

        String treeJson = fetchUrlToString(apiBaseUrl + "/accounts/" + accountId
                + "/folder-tree?expand=experiences");

        JsonNode tree = MAPPER.readTree(treeJson);

        // The API returns { "resources": [ {folder}, {folder}, ... ] }
        // Each folder has accountSlug, children[], and experiences.resources[]
        ArrayNode folders = MAPPER.createArrayNode();
        JsonNode resources = tree.path("resources");
        if (resources.isArray()) {
            for (JsonNode folderNode : resources) {
                String accountSlug = folderNode.path("accountSlug").asText("");
                ObjectNode folder = processFolder(folderNode, accountSlug);
                if (folder != null) {
                    folders.add(folder);
                }
            }
        }

        ObjectNode result = MAPPER.createObjectNode();
        result.put("accountName", accountName);
        result.set("folders", folders);
        return MAPPER.writeValueAsString(result);
    }

    private ObjectNode processFolder(JsonNode folderNode, String accountSlug) {
        ObjectNode folder = MAPPER.createObjectNode();
        folder.put("resourceId", folderNode.path("resourceId").asText(""));
        folder.put("name", folderNode.path("name").asText(""));

        // Process child folders recursively
        ArrayNode children = MAPPER.createArrayNode();
        if (folderNode.has("children") && folderNode.get("children").isArray()) {
            for (JsonNode child : folderNode.get("children")) {
                ObjectNode childFolder = processFolder(child, accountSlug);
                if (childFolder != null) {
                    children.add(childFolder);
                }
            }
        }
        folder.set("children", children);

        // Process experiences — only include published ones
        ArrayNode experiences = MAPPER.createArrayNode();
        JsonNode expsNode = folderNode.path("experiences").path("resources");
        if (expsNode.isArray()) {
            for (JsonNode exp : expsNode) {
                String status = exp.path("status").asText("");
                if (!"published".equals(status)) {
                    continue;
                }

                String slug = exp.path("primaryAliasSlug").asText("");
                String manifestUrl = "";
                if (StringUtils.isNotBlank(accountSlug) && StringUtils.isNotBlank(slug)) {
                    // accountSlug is a subdomain: https://{accountSlug}.{viewBaseDomain}/{slug}/manifest.json
                    String viewUrl = viewBaseUrl.replaceFirst("^(https?://)", "$1" + accountSlug + ".");
                    manifestUrl = viewUrl + "/" + slug + "/" + CerosConstants.DEFAULT_ASSET_FILE_PATH;
                }

                ObjectNode expOut = MAPPER.createObjectNode();
                expOut.put("resourceId", exp.path("resourceId").asText(""));
                expOut.put("name", exp.path("name").asText(""));
                expOut.put("thumbnailUrl", exp.path("thumbnailUrl").asText(""));
                expOut.put("lastPublishedDate", exp.path("lastPublishedDate").asText(""));
                expOut.put("manifestUrl", manifestUrl);
                experiences.add(expOut);
            }
        }
        folder.set("experiences", experiences);

        return folder;
    }

    private String resolveAccountId() throws IOException {
        String cached = cachedAccountResourceId;
        if (cached != null) {
            return cached;
        }

        String json = fetchUrlToString(apiBaseUrl + "/accounts/current-account");
        JsonNode node = MAPPER.readTree(json);
        String accountId = node.path("accountResourceId").asText(null);
        if (accountId == null) {
            throw new IOException("Could not resolve accountResourceId from Ceros API");
        }
        cachedAccountResourceId = accountId;
        cachedAccountName = node.path("accountName").asText("");
        return accountId;
    }

    private String fetchUrlToString(String urlStr) throws IOException {
        return HttpUtils.fetchString(urlStr, httpTimeoutMillis,
                Map.of("Accept", "application/json",
                       "Authorization", "Bearer " + apiKey));
    }
}
