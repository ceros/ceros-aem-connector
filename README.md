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

See [Adding this plugin to your AEM project](#adding-this-plugin-to-your-aem-project) below.

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

## Adding this plugin to your AEM project

The plugin is published to Maven Central. Pull the latest version from
[search.maven.org](https://search.maven.org/artifact/com.ceros/ceros-aem-connector-all)
or look at the [releases](https://github.com/ceros/ceros-aem-connector/releases)
page; the snippets below use `0.0.2` as an example.

### Cloud Manager / filevault build (recommended)

If your AEM project uses the standard archetype with an `all` content-package
module, embed the Ceros connector as a sub-package so it ships in the same
deployable as your customisations.

In your project's `all/pom.xml`:

```xml
<dependency>
    <groupId>com.ceros</groupId>
    <artifactId>ceros-aem-connector-all</artifactId>
    <version>0.0.2</version>
    <type>zip</type>
</dependency>
```

…and in the `filevault-package-maven-plugin` configuration in the same file,
add it under `<subPackages>` so it lands at `/apps/ceros-packages/...` at
install time:

```xml
<plugin>
    <groupId>org.apache.jackrabbit</groupId>
    <artifactId>filevault-package-maven-plugin</artifactId>
    <configuration>
        <subPackages>
            <subPackage>
                <groupId>com.ceros</groupId>
                <artifactId>ceros-aem-connector-all</artifactId>
                <filter>true</filter>
            </subPackage>
        </subPackages>
    </configuration>
</plugin>
```

`<filter>true</filter>` extends your container package's filter with the
connector's filter, so Cloud Manager's package validator accepts the merged
install.

No `<exclusions>` block is needed — from `0.0.2` onward the published POM marks
the embedded sub-modules as `<optional>true</optional>`, so Maven does not
attempt to resolve `ceros-aem-connector-core` / `ui.apps` / `ui.config`
transitively.

### Manual install (single AEM instance)

For a one-off install on a dev / sandbox instance, download the zip directly
from Maven Central:

```
https://repo1.maven.org/maven2/com/ceros/ceros-aem-connector-all/0.0.2/ceros-aem-connector-all-0.0.2.zip
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
authoring sidekick under `Ceros / Ceros Flex`. Drop it onto a page and confirm
the dialog opens.

## Development

See [DEVELOPMENT-SETUP.md](DEVELOPMENT-SETUP.md).

## License

[BSD 3-Clause License](LICENSE)
