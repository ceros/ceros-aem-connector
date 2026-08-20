package com.ceros.services.impl;

import com.ceros.CerosConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CerosAuthenticatedApiServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CerosAuthenticatedApiServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CerosAuthenticatedApiServiceImpl();
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CerosAuthenticatedApiServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    void isEnabledReturnsFalseWhenNoApiKey() {
        assertFalse(service.isEnabled());
    }

    @Test
    void isEnabledReturnsTrueWhenApiKeySet() throws Exception {
        setField("apiKey", "test-key");
        assertTrue(service.isEnabled());
    }

    @Test
    void getFolderTreeJsonThrowsWhenNotEnabled() {
        assertThrows(java.io.IOException.class, () -> service.getFolderTreeJson());
    }

    @Test
    void activateAppliesDefaultApiVersion() throws Exception {
        service.activate(configWithApiVersion("2026-08-06-09-00"));

        Field f = CerosAuthenticatedApiServiceImpl.class.getDeclaredField("apiVersion");
        f.setAccessible(true);
        assertEquals("2026-08-06-09-00", f.get(service));
    }

    @Test
    void activateFallsBackToDefaultApiVersionWhenBlank() throws Exception {
        for (String blank : new String[] { "", "   ", "\t" }) {
            service.activate(configWithApiVersion(blank));

            Field f = CerosAuthenticatedApiServiceImpl.class.getDeclaredField("apiVersion");
            f.setAccessible(true);
            assertEquals(CerosConstants.DEFAULT_FLEX_API_VERSION, f.get(service),
                    "a blank configured version should fall back to the default");
        }
    }

    @Test
    void activateTrimsAndKeepsConfiguredApiVersion() throws Exception {
        service.activate(configWithApiVersion("  2099-01-01-00-00  "));

        Field f = CerosAuthenticatedApiServiceImpl.class.getDeclaredField("apiVersion");
        f.setAccessible(true);
        assertEquals("2099-01-01-00-00", f.get(service),
                "a non-blank override should win over the default");
    }

    @Test
    void sendsApiVersionHeaderOnEveryRequest() throws Exception {
        List<Headers> received = new ArrayList<>();
        HttpServer server = startStubApi(received);
        try {
            pointServiceAt(server, "2026-08-06-09-00");

            service.getFolderTreeJson();

            assertEquals(2, received.size(),
                    "expected current-account then folder-tree requests");
            for (Headers headers : received) {
                assertEquals("2026-08-06-09-00", headers.getFirst("x-ceros-api-version"));
                assertEquals("Bearer test-key", headers.getFirst("Authorization"));
                assertEquals("application/json", headers.getFirst("Accept"));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsDefaultApiVersionHeaderWhenConfigBlank() throws Exception {
        List<Headers> received = new ArrayList<>();
        HttpServer server = startStubApi(received);
        try {
            // Real activate + real request path: an empty OSGi value must still
            // put the default version on the wire, never a blank/absent header.
            service.activate(config("", baseUrlOf(server)));

            service.getFolderTreeJson();

            assertEquals(2, received.size());
            for (Headers headers : received) {
                assertEquals(CerosConstants.DEFAULT_FLEX_API_VERSION,
                        headers.getFirst("x-ceros-api-version"));
            }
        } finally {
            server.stop(0);
        }
    }

    private void pointServiceAt(HttpServer server, String apiVersion) throws Exception {
        setField("apiKey", "test-key");
        setField("apiVersion", apiVersion);
        setField("apiBaseUrl", baseUrlOf(server));
        setField("viewBaseUrl", "https://ceros.site");
        setField("httpTimeoutMillis", 5000);
    }

    private String baseUrlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Stands up a real loopback endpoint for the two calls
     * {@code getFolderTreeJson} makes, recording the headers each one actually
     * sent so the production request path is verified rather than re-implemented.
     */
    private HttpServer startStubApi(List<Headers> received) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/accounts/current-account", exchange -> respond(received, exchange,
                "{\"accountResourceId\":\"acct-1\",\"accountName\":\"Test Account\"}"));
        server.createContext("/accounts/acct-1/folder-tree", exchange -> respond(received, exchange,
                "{\"resources\":[]}"));
        server.start();
        return server;
    }

    private void respond(List<Headers> received, HttpExchange exchange, String body)
            throws IOException {
        Headers snapshot = new Headers();
        snapshot.putAll(exchange.getRequestHeaders());
        received.add(snapshot);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private CerosAuthenticatedApiServiceImpl.Config configWithApiVersion(String version) {
        return config(version, "https://rest.ceros.com");
    }

    private CerosAuthenticatedApiServiceImpl.Config config(String version, String baseUrl) {
        return (CerosAuthenticatedApiServiceImpl.Config) Proxy.newProxyInstance(
                CerosAuthenticatedApiServiceImpl.Config.class.getClassLoader(),
                new Class<?>[] { CerosAuthenticatedApiServiceImpl.Config.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "flexApiVersion":
                            return version;
                        case "flexApiKey":
                            return "test-key";
                        case "flexApiBaseUrl":
                            return baseUrl;
                        case "flexViewBaseUrl":
                            return "https://ceros.site";
                        case "httpTimeoutSeconds":
                            return 30;
                        default:
                            return method.getDefaultValue();
                    }
                });
    }

    @Test
    void processFolderCreatesStructuredOutput() throws Exception {
        Method processFolder = CerosAuthenticatedApiServiceImpl.class.getDeclaredMethod(
                "processFolder", JsonNode.class, String.class);
        processFolder.setAccessible(true);

        ObjectNode folderNode = MAPPER.createObjectNode();
        folderNode.put("resourceId", "folder-1");
        folderNode.put("name", "My Folder");

        ObjectNode expNode = MAPPER.createObjectNode();
        expNode.put("resourceId", "exp-1");
        expNode.put("name", "Test Experience");
        expNode.put("status", "published");
        expNode.put("primaryAliasSlug", "test-exp");
        expNode.put("thumbnailUrl", "https://thumb.jpg");
        expNode.put("lastPublishedDate", "2025-01-01");

        ObjectNode unpubExp = MAPPER.createObjectNode();
        unpubExp.put("resourceId", "exp-2");
        unpubExp.put("status", "draft");

        ArrayNode expResources = MAPPER.createArrayNode();
        expResources.add(expNode);
        expResources.add(unpubExp);

        ObjectNode experiences = MAPPER.createObjectNode();
        experiences.set("resources", expResources);
        folderNode.set("experiences", experiences);

        ObjectNode childFolder = MAPPER.createObjectNode();
        childFolder.put("resourceId", "subfolder-1");
        childFolder.put("name", "Sub Folder");
        ArrayNode children = MAPPER.createArrayNode();
        children.add(childFolder);
        folderNode.set("children", children);

        setField("viewBaseUrl", "https://ceros.site");

        ObjectNode result = (ObjectNode) processFolder.invoke(service, folderNode, "myaccount");

        assertEquals("folder-1", result.get("resourceId").asText());
        assertEquals("My Folder", result.get("name").asText());

        ArrayNode exps = (ArrayNode) result.get("experiences");
        assertEquals(1, exps.size());
        assertEquals("exp-1", exps.get(0).get("resourceId").asText());
        assertEquals("Test Experience", exps.get(0).get("name").asText());
        assertEquals("https://myaccount.ceros.site/test-exp/manifest.v1.json",
                exps.get(0).get("manifestUrl").asText());

        ArrayNode kids = (ArrayNode) result.get("children");
        assertEquals(1, kids.size());
        assertEquals("subfolder-1", kids.get(0).get("resourceId").asText());
    }

    @Test
    void processFolderHandlesEmptyExperiences() throws Exception {
        Method processFolder = CerosAuthenticatedApiServiceImpl.class.getDeclaredMethod(
                "processFolder", JsonNode.class, String.class);
        processFolder.setAccessible(true);

        ObjectNode folderNode = MAPPER.createObjectNode();
        folderNode.put("resourceId", "folder-1");
        folderNode.put("name", "Empty Folder");

        setField("viewBaseUrl", "https://ceros.site");

        ObjectNode result = (ObjectNode) processFolder.invoke(service, folderNode, "acct");

        assertEquals(0, result.get("experiences").size());
        assertEquals(0, result.get("children").size());
    }

    @Test
    void processFolderOmitsManifestUrlWhenSlugMissing() throws Exception {
        Method processFolder = CerosAuthenticatedApiServiceImpl.class.getDeclaredMethod(
                "processFolder", JsonNode.class, String.class);
        processFolder.setAccessible(true);

        ObjectNode folderNode = MAPPER.createObjectNode();
        folderNode.put("resourceId", "folder-1");
        folderNode.put("name", "Folder");

        ObjectNode exp = MAPPER.createObjectNode();
        exp.put("resourceId", "exp-1");
        exp.put("name", "No Slug Exp");
        exp.put("status", "published");

        ArrayNode resources = MAPPER.createArrayNode();
        resources.add(exp);
        ObjectNode experiences = MAPPER.createObjectNode();
        experiences.set("resources", resources);
        folderNode.set("experiences", experiences);

        setField("viewBaseUrl", "https://ceros.site");

        ObjectNode result = (ObjectNode) processFolder.invoke(service, folderNode, "acct");

        ArrayNode exps = (ArrayNode) result.get("experiences");
        assertEquals(1, exps.size());
        assertEquals("", exps.get(0).get("manifestUrl").asText());
    }

    @Test
    void processFolderOmitsManifestUrlWhenAccountSlugEmpty() throws Exception {
        Method processFolder = CerosAuthenticatedApiServiceImpl.class.getDeclaredMethod(
                "processFolder", JsonNode.class, String.class);
        processFolder.setAccessible(true);

        ObjectNode folderNode = MAPPER.createObjectNode();
        folderNode.put("resourceId", "folder-1");
        folderNode.put("name", "Folder");

        ObjectNode exp = MAPPER.createObjectNode();
        exp.put("resourceId", "exp-1");
        exp.put("status", "published");
        exp.put("primaryAliasSlug", "my-slug");

        ArrayNode resources = MAPPER.createArrayNode();
        resources.add(exp);
        ObjectNode experiences = MAPPER.createObjectNode();
        experiences.set("resources", resources);
        folderNode.set("experiences", experiences);

        setField("viewBaseUrl", "https://ceros.site");

        ObjectNode result = (ObjectNode) processFolder.invoke(service, folderNode, "");

        assertEquals("", result.get("experiences").get(0).get("manifestUrl").asText());
    }

    @Test
    void processFolderRecursesChildren() throws Exception {
        Method processFolder = CerosAuthenticatedApiServiceImpl.class.getDeclaredMethod(
                "processFolder", JsonNode.class, String.class);
        processFolder.setAccessible(true);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("resourceId", "root");
        root.put("name", "Root");

        ObjectNode child = MAPPER.createObjectNode();
        child.put("resourceId", "child");
        child.put("name", "Child");

        ObjectNode grandchild = MAPPER.createObjectNode();
        grandchild.put("resourceId", "grandchild");
        grandchild.put("name", "Grandchild");

        ArrayNode grandChildren = MAPPER.createArrayNode();
        grandChildren.add(grandchild);
        child.set("children", grandChildren);

        ArrayNode childrenArr = MAPPER.createArrayNode();
        childrenArr.add(child);
        root.set("children", childrenArr);

        setField("viewBaseUrl", "https://ceros.site");

        ObjectNode result = (ObjectNode) processFolder.invoke(service, root, "acct");

        ArrayNode kids = (ArrayNode) result.get("children");
        assertEquals(1, kids.size());
        ArrayNode grandkids = (ArrayNode) kids.get(0).get("children");
        assertEquals(1, grandkids.size());
        assertEquals("Grandchild", grandkids.get(0).get("name").asText());
    }

    @Test
    void manifestUrlConstructedCorrectly() throws Exception {
        Method processFolder = CerosAuthenticatedApiServiceImpl.class.getDeclaredMethod(
                "processFolder", JsonNode.class, String.class);
        processFolder.setAccessible(true);

        ObjectNode folderNode = MAPPER.createObjectNode();
        folderNode.put("resourceId", "f1");
        folderNode.put("name", "F");

        ObjectNode exp = MAPPER.createObjectNode();
        exp.put("resourceId", "e1");
        exp.put("status", "published");
        exp.put("primaryAliasSlug", "my-experience");

        ArrayNode resources = MAPPER.createArrayNode();
        resources.add(exp);
        ObjectNode experiences = MAPPER.createObjectNode();
        experiences.set("resources", resources);
        folderNode.set("experiences", experiences);

        setField("viewBaseUrl", "https://ceros.site");
        ObjectNode result = (ObjectNode) processFolder.invoke(service, folderNode, "company");
        assertEquals("https://company.ceros.site/my-experience/manifest.v1.json",
                result.get("experiences").get(0).get("manifestUrl").asText());

        setField("viewBaseUrl", "http://ceros.site");
        result = (ObjectNode) processFolder.invoke(service, folderNode, "company");
        assertEquals("http://company.ceros.site/my-experience/manifest.v1.json",
                result.get("experiences").get(0).get("manifestUrl").asText());
    }
}
