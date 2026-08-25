# Ceros AEM Plugin

An AEM plugin that provides the **Ceros Flex** component for embedding
[Ceros](https://www.ceros.com/) experiences into AEM pages.

The build produces a single content package —
`ceros-aem-connector-all-<version>.zip` — that bundles the OSGi bundle,
component definitions, and default configuration for one-step deployment.

The artifact is published to Maven Central under:

```
com.ceros:ceros-aem-connector-all:<version>
```

## Adding this plugin to your AEM project

The plugin is published to Maven Central. Pull the latest version from
[search.maven.org](https://search.maven.org/artifact/com.ceros/ceros-aem-connector-all)
or look at the [releases](https://github.com/ceros/ceros-aem-connector/releases)
page; the snippets below use `1.0.3` as an example.

### Cloud Manager / filevault build (recommended)

If your AEM project uses the standard archetype with an `all` content-package
module, embed `ceros-aem-connector-all` into that container package so the
connector ships in the same deployable as your own customisations.

Three files need editing. Declaring the Maven dependency on its own is **not**
enough — it resolves the artifact but does not place it inside your package, so
the connector would silently be absent from the build with no error.

The examples below assume an archetype-generated project whose `appId` is
`myproject`; substitute your own throughout.

#### 1. Root `pom.xml` — pin the version

Add the artifact to `<dependencyManagement>` so the version lives in one place:

```xml
<dependencyManagement>
    <dependencies>
        <!-- Ceros AEM Plugin -->
        <dependency>
            <groupId>com.ceros</groupId>
            <artifactId>ceros-aem-connector-all</artifactId>
            <version>1.0.3</version>
            <type>zip</type>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The artifact is on Maven Central, so no extra `<repositories>` entry is needed.
Upgrading the connector later is a one-line change here.

#### 2. `all/pom.xml` — declare *and* embed

Two separate edits in this file. 

First, add the dependency with version omitted — it
comes from the managed entry above):

```xml
<dependencies>
    <!-- Ceros AEM Plugin -->
    <dependency>
        <groupId>com.ceros</groupId>
        <artifactId>ceros-aem-connector-all</artifactId>
        <type>zip</type>
    </dependency>
</dependencies>
```

Second add an `<embedded>` block to
the `filevault-package-maven-plugin` configuration, alongside the embeds for
your own `ui.apps` / `core` / `ui.content` modules:

```xml
<plugin>
    <groupId>org.apache.jackrabbit</groupId>
    <artifactId>filevault-package-maven-plugin</artifactId>
    <extensions>true</extensions>
    <configuration>
        <packageType>container</packageType>
        <embeddeds>
            <!-- …your own module embeds… -->

            <!-- Ceros AEM Plugin -->
            <embedded>
                <groupId>com.ceros</groupId>
                <artifactId>ceros-aem-connector-all</artifactId>
                <type>zip</type>
                <target>/apps/myproject-vendor-packages/container/install</target>
            </embedded>
        </embeddeds>
    </configuration>
</plugin>
```

The `<target>` matters. `ceros-aem-connector-all` is itself a **container**
package, so it belongs under `vendor-packages/container/install`.
Putting it under `packages/application/install` (where
your own bundles and `ui.apps` go) fails AEM's package-type validation.

#### 3. `all/src/main/content/META-INF/vault/filter.xml` — cover the path

The embedded zip is written to a path that must fall
inside your container package's workspace filter. If it doesn't, filevault
treats it as content outside the filter roots depending on plugin version
that either fails validation or drops it from the artifact.
Archetype-generated projects usually ship with the correct roots already, but confirm the
`vendor-packages` one is present in your project.

```xml
<workspaceFilter version="1.0">
    <filter root="/apps/myproject-packages"/>
    <filter root="/apps/myproject-vendor-packages"/>
</workspaceFilter>
```
#### Confirm the wiring

After `mvn clean install`, list the container package and check the connector
is actually inside it:

```bash
unzip -l all/target/*.all-*.zip | grep ceros-aem-connector
```

You should see one line, roughly:

```
jcr_root/apps/myproject-vendor-packages/container/install/ceros-aem-connector-all-1.0.3.zip
```

No line means one of the three steps above is missing. This check is worth
wiring into CI — a dropped `<embedded>` produces a green build that deploys
nothing.

### Manual install (single AEM instance)

For a one-off install on a dev / sandbox instance, download the zip directly
from Maven Central:

```
https://repo1.maven.org/maven2/com/ceros/ceros-aem-connector-all/1.0.3/ceros-aem-connector-all-1.0.3.zip
```

Upload via **CRX Package Manager** at `/crx/packmgr/index.jsp` and install.

### Configure the plugin

Once installed, supply your Flex API key via OSGi config — see
[CONFIGURATION.md](CONFIGURATION.md) for the full list of properties. The
shipped config uses AEM secrets:

```
$[secret:flexApiKey]
```

so the key never lives in the repo. Set the value via Cloud Manager's
environment variables or the OSGi console on an on-prem instance.

### Verify

After deploying, you should see the Ceros component available in the
authoring sidekick under `Ceros Flex`. Drop it onto a page and confirm
the dialog opens.

## Prerequisites

- Java 17+
- Maven 3.6+
- AEM as a Cloud Service SDK

## Building

```bash
mvn clean install
```

This compiles all modules, runs unit tests, and installs artifacts to your
local Maven repository.

## Delivery Modes

The Ceros Flex component supports five delivery modes, selectable per component
instance in the authoring dialog. `CerosDeliveryMode` is the single source of
truth for the stored mode strings; at render time `CerosFlexModel` delegates to
`CerosFlexDeliveryService`, which calls `DeliveryHandler.forMode(...)` to
dispatch to the matching handler. Unknown or blank modes fall back to **Fetch**
so legacy components keep working.

The modes fall into two families:

- **Server-side (SSR)** — the manifest is resolved on the server and the
  experience markup is rendered into the page.
- **Client-side** — the page emits a small marker plus a Ceros runtime script
  that renders the experience in the browser.

### Fetch — server-side (`fetch`)

At page render time, `FetchDeliveryHandler` calls `CerosManifestService` to
fetch the manifest JSON from the Ceros CDN on every render, and renders its CSS,
JS, and HTML into the page. If a deep-link query param targets a different page
of the experience, that page's manifest is fetched instead. The published page
makes runtime requests to the Ceros CDN.

### Store — server-side (`store`)

An author triggers a fetch from the dialog. `CerosManifestStoreServlet` fetches
the manifest, then `CerosAssetStorageService` downloads all referenced assets
(CSS, JS, fonts, media) and uploads them to the AEM DAM. Manifest URLs are
rewritten to point at the DAM copies, and the rewritten manifest bundle is
persisted on the component node. At render time `StoreDeliveryHandler` reads the
stored bundle and serves the page matching the deep-link param — fully offline,
with **no runtime CDN dependency**.

### HTML Import — server-side (`import`)

Instead of a live CDN fetch, the author uploads a Ceros export archive
(`.tar.gz`) from the dialog. `CerosImportArchiveServlet` hands it to
`CerosManifestService.performImportAndStore`, which unpacks the archive (with a
zip-bomb size guard), uploads its assets and manifests to the DAM, and persists
the same stored bundle on the component. Because the end state is identical to
**Store** (bundle on the component + assets in DAM), it renders through the same
`StoreDeliveryHandler` and is likewise fully offline.

### Inline — client-side (`inline`)

`InlineDeliveryHandler` emits the Ceros inline marker
(`<div data-flex-inline data-flex-manifest-url=…>`) plus the `flex-client.js`
runtime, which fetches the manifest in the browser and renders the experience
into a Shadow Root in the host page DOM — no SSR markup and no iframe. The
runtime URL is grabbed from the manifest at authoring time and persisted on the
component (see `CerosFlexManifestUrlPostProcessor`), so render makes no network call.

### Embed — client-side (`embed`)

`EmbedDeliveryHandler` renders a lightweight iframe embed using the Ceros embed
script. No manifest processing is required — the embed script loads everything
client-side.

## Experience Browsing

When a Flex API key is configured, the authoring dialog provides a **Browse**
mode powered by `CerosAuthenticatedApiService`. Authors can navigate the
account's folder tree and pick a published experience instead of pasting a
manifest URL manually.

## Experience Metadata

Every component stores the ID of the Ceros experience it points at, on the
component node as `cerosExperienceResourceId`. It is metadata only — nothing
renders it and delivery never reads it. It exists so authored experiences can be
found by ID rather than by string-matching manifest URLs, which break whenever a
slug or alias changes.

It is read from `experience.experienceResourceId` in the manifest, from whichever
copy of the manifest that mode already has:

- **URL-based modes** (Fetch, Store, Inline, Iframe embed) derive it on dialog
  save, from the manifest for the stored Ceros Experience URL.
- **HTML Import** derives it when the archive is unpacked, from the exported
  manifest — no network call.

Because it is re-derived from the current manifest each time, it cannot drift out
of step with the experience the component points at.

The property is blank for experiences last published or exported before the
manifest carried the field; republishing in Ceros fills it in the next time the
component is saved or re-imported.

### Querying it

A lookup like

```sql
SELECT * FROM [nt:unstructured] WHERE [cerosExperienceResourceId] = 'exp-123'
```

needs an Oak index to be served efficiently. Without one it traverses the
repository — fine for occasional admin use in Query Builder, but not at scale,
where Oak logs traversal warnings and AEM as a Cloud Service may refuse the
query outright.

Check whether an index covering this property already exists on your instance
before relying on the query. To add one to your own project:

```xml
<cerosExperienceResourceId-1-custom
    jcr:primaryType="oak:QueryIndexDefinition"
    type="property"
    propertyNames="{Name}[cerosExperienceResourceId]"
    reindex="{Boolean}true"/>
```

Deploy it under `/oak:index`, covered by your package's workspace filter, and
follow the AEM as a Cloud Service custom-index naming convention
(`<name>-<productVersion>-custom`).

## Project Structure

```
core/        Java: Sling Model (CerosFlexModel), services, servlets, DTOs, utilities
ui.apps/     AEM component: definition, dialog, HTL template under /apps/connectors/ceros/components/cerosflex
ui.config/   Default OSGi configuration for the Flex API service
all/         Single content package that embeds core, ui.apps, and ui.config
```

## Configuration

See [CONFIGURATION.md](CONFIGURATION.md) for all OSGi properties, defaults,
and example `.cfg.json` files.

## Development

See [DEVELOPMENT-SETUP.md](DEVELOPMENT-SETUP.md).

## License

[BSD 3-Clause License](LICENSE)
