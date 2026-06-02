# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/ceros/ceros-aem-connector/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/ceros/ceros-aem-connector/releases/tag/v0.0.1
