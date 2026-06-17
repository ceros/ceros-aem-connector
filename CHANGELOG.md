# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.5] - 2026-06-17

### Added
- HTML-import delivery mode: authors can upload a Ceros `.tar.gz` export in the cerosflex dialog and have it unpacked into the DAM, then served fully offline with no CDN dependency. `CerosImportArchiveServlet` (`/bin/ceros/import-archive`) streams the upload to `/var/ceros/imports/<jobId>`, and `CerosImportArchiveJobConsumer` (topic `com/ceros/import-archive`) unpacks it, stores assets + manifests to DAM, and deletes the transient archive on success. The stored end state matches store mode, so it renders through the existing `StoreDeliveryHandler` and `.preview.html` servlet.
- `ArchiveUtils` — dependency-free `.tar.gz` reader (JDK gunzip + minimal USTAR) that strips the wrapper directory, with zip-slip and uncompressed-size guards.
- `CerosAssetStorageService.uploadAssetsFromArchive` — archive-sourced asset upload that mirrors the relative archive layout into `<damBasePath>/<expSlug>/<pageSlug>/` and rewrites manifest URLs to the DAM copies.

### Changed
- `CerosManifestService.storeManifestBundle` is mode-parameterised so import persists `cerosMode=import` (and `manifestUrl` pointing at the primary DAM manifest). `ServletUtils` gains a shared `normaliseComponentPath` reused by the fetch and import servlets.
- Authoring dialog adds the **Server-side (HTML Import)** option with a file picker that uploads on selection (loading state, no separate button); fetch-only widgets (Browse Experiences, Ceros Experience URL, Last Fetched) are hidden in import mode. Status polling reuses the existing `/bin/ceros/fetch-manifest-status` endpoint, and repoinit adds `/var/ceros/imports`.

## [0.0.4] - 2026-06-12

### Added
- Manifest fetch now runs as a Sling Job with a polled `cerosflex.fetch-status` endpoint and a new `JcrFetchProgress` writer, so the authoring dialog can show live progress instead of a long-blocking request.
- Store mode mirrors each page's manifest into DAM at `<damBasePath>/<expSlug>/<pageSlug>/manifest.json`, with `pages[].manifestUrl` rewritten to sibling DAM URLs. `data-flex-manifest-url` points at the DAM copy so the in-browser SPA router navigates entirely inside AEM — no Ceros CDN call at click time.
- New `CerosFlexPreviewServlet` serves `<componentPath>.preview.html` — a standalone, chrome-free render of the experience used as the iframe source for the author-mode store preview.

### Changed
- Switched manifest schema from `manifest.v0.json` to `manifest.v1.json`. The model classes mirror the v1 layout (`CerosManifestV1`, new `pages[].manifestUrl` setter).
- Author-mode preview in store mode no longer points at the external Ceros CDN; it loads the local preview servlet instead, keeping the editor consistent with store mode's offline guarantee.

## [0.0.3] - 2026-06-12

### Added
- Multi-page store mode: store deliveries now persist a `StoredManifestBundle` (primary + per-page manifests) so any deep-linked page can be served fully offline. `CerosManifestService` gains `fetchManifestBundle` / `storeManifestBundle`.
- README documents how to consume the published plugin from Maven Central (Cloud Manager / filevault `subPackage` embedding and manual CRX install).

### Changed
- Delivery layer refactor: per-mode dispatch extracted into `com.ceros.delivery` (`DeliveryResult`, `ManifestRenderer`, `DeepLinkResolver`) and `com.ceros.delivery.modes` (Fetch / Store / Embed handlers, `DeliveryHandler.forMode` factory).
- `CerosFlexModel` split into a data POJO and a `CerosFlexView` Sling Model; delivery orchestration moved to the new `CerosFlexDeliveryService`. HTL binds `CerosFlexView`.
- `ServletUtils` moved to `com.ceros.util`; added `ManifestUtils.primarySlugOf`.

### Removed
- Inline shared-services stub and the legacy single-manifest parse fallback in `StoredManifestBundle`.

## [0.0.2] - 2026-06-02

### Changed
- `ceros-aem-connector-all`'s published POM marks `core` / `ui.apps` / `ui.config` sub-module dependencies as `<optional>true</optional>`. Consumers can now depend on `ceros-aem-connector-all` without an `<exclusions>` block — the sub-modules are embedded inside the content-package zip and are not published to Maven Central.

## [0.0.1] - 2026-06-02

Initial release.

### Added
- `cerosflex` AEM component with three delivery modes:
  - **Server-side (Always Fetch)** — loads the Ceros manifest on every page render.
  - **Server-side (Store)** — fetches once and persists the manifest to JCR.
  - **Client-side (Iframe embed)** — injects the Ceros embed script for client-side rendering.
- `CerosManifestService` and `CerosManifestStoreServlet` for fetching, parsing, and persisting Ceros experience manifests.
- DAM upload of manifest-referenced assets using the manifest's declared `mimeType` rather than file-extension guessing.
- Preservation of Ceros anchor links from the AEM link checker; manifest URL is exposed for downstream tooling.
- Editor-view iframe previews suppress Ceros analytics to keep author traffic out of customer metrics.
- External `<script>` elements injected into the body strip `crossorigin` and `type=module` to avoid AEM dispatcher conflicts.
- OSGi configuration support (timeouts, HTTP scheme allowlist, local-address allowlist) via `CerosManifestServiceImpl.cfg.json`.
- Authenticated browsing of Ceros Flex experiences in the authoring dialog via `CerosAuthenticatedApiService`.

[Unreleased]: https://github.com/ceros/ceros-aem-connector/compare/release-0.0.3...HEAD
[0.0.3]: https://github.com/ceros/ceros-aem-connector/releases/tag/release-0.0.3
[0.0.2]: https://github.com/ceros/ceros-aem-connector/releases/tag/release-0.0.2
[0.0.1]: https://github.com/ceros/ceros-aem-connector/releases/tag/release-0.0.1
