# Ceros AEM Plugin

An AEM plugin that provides the **Ceros Flex** component for embedding
[Ceros](https://www.ceros.com/) experiences into AEM pages.

The build produces a single content package — `ceros-aem-connector-all-0.0.1.zip` —
that bundles the OSGi bundle, component definitions, and default configuration
for one-step deployment.

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

The Ceros Flex component supports three delivery modes, selectable per
component instance in the authoring dialog:

### Fetch (client-side)

At page render time, `CerosFlexModel` calls `CerosPublicManifestService` to
fetch the manifest JSON from the Ceros CDN. CSS, JS, and HTML assets are
inlined into the page. The published page makes runtime requests to the Ceros
CDN.

### Store (server-side)

An author triggers a fetch from the dialog. The `CerosManifestStoreServlet`
fetches the manifest, then `CerosAssetStorageService` downloads all referenced
assets (CSS, JS, fonts, media) and uploads them to AEM DAM. Manifest URLs are
rewritten to point at the DAM copies and the rewritten manifest JSON is
persisted on the component node. The published page has **no runtime CDN
dependency**.

### Embed (iframe)

Renders a lightweight iframe embed using the Ceros embed script. No manifest
processing is required.

## Experience Browsing

When a Flex API key is configured, the authoring dialog provides a **Browse**
mode powered by `CerosAuthenticatedApiService`. Authors can navigate the
account's folder tree and pick a published experience instead of pasting a
manifest URL manually.

## Project Structure

```
core/        Java: Sling Model (CerosFlexModel), services, servlets, DTOs, utilities
ui.apps/     AEM component: definition, dialog, HTL template under /apps/ceros-flex/components/cerosflex
ui.config/   Default OSGi configuration for the Flex API service
all/         Single content package that embeds core, ui.apps, and ui.config
```

## Configuration

See [CONFIGURATION.md](CONFIGURATION.md) for all OSGi properties, defaults,
and example `.cfg.json` files.

## Installation

Install the content package on your AEM instance:

```
all/target/ceros-aem-connector-all-0.0.1.zip
```

## Development

See [DEVELOPMENT-SETUP.md](DEVELOPMENT-SETUP.md).

## License

[BSD 3-Clause License](LICENSE)
