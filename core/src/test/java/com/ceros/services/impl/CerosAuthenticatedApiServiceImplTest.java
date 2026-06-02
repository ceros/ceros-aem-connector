package com.ceros.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        assertEquals("https://myaccount.ceros.site/test-exp/manifest.v0.json",
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
        assertEquals("https://company.ceros.site/my-experience/manifest.v0.json",
                result.get("experiences").get(0).get("manifestUrl").asText());

        setField("viewBaseUrl", "http://ceros.site");
        result = (ObjectNode) processFolder.invoke(service, folderNode, "company");
        assertEquals("http://company.ceros.site/my-experience/manifest.v0.json",
                result.get("experiences").get(0).get("manifestUrl").asText());
    }
}
