# Configuration

All services are configured via OSGi. The default configuration ships in the
`ui.config` module and is deployed automatically with the `all` package.

Override values per environment by placing `.cfg.json` files in the appropriate
runmode folder, e.g.:

```
/apps/connectors/ceros/osgiconfig/config.prod/com.ceros.services.impl.CerosAuthenticatedApiServiceImpl.cfg.json
/apps/connectors/ceros/osgiconfig/config.prod/com.ceros.services.impl.CerosAssetStorageServiceImpl.cfg.json
```

---

## Manifest Service (`CerosPublicManifestServiceImpl`)

Fetches and parses Ceros experience manifests from public URLs.

| Property | Default | Description |
|----------|---------|-------------|
| `httpTimeoutSeconds` | `30` | HTTP timeout for fetching manifests |

---

## Flex API Service (`CerosAuthenticatedApiServiceImpl`)

Enables the **Browse** mode in the authoring dialog. When no API key is
configured the service disables itself and the component works with manual URL
entry only.

| Property | Default | Description |
|----------|---------|-------------|
| `flexApiKey` | _(empty — disables browse)_ | API token for the Ceros Flex API |
| `flexApiBaseUrl` | `https://rest.ceros.com` | Base URL for the Flex REST API |
| `flexViewBaseUrl` | `https://ceros.site` | Base domain for manifest URLs |
| `httpTimeoutSeconds` | `30` | HTTP timeout |

Example `.cfg.json`:

```json
{
    "flexApiKey": "YOUR_CEROS_API_KEY_HERE",
    "flexApiBaseUrl": "https://rest.ceros.com",
    "flexViewBaseUrl": "https://ceros.site",
    "httpTimeoutSeconds:Integer": 30
}
```

---

## Asset Storage Service (`CerosAssetStorageServiceImpl`)

Mirrors assets into AEM DAM. Used by **Store** and **Import** modes. All
properties have sensible defaults — no secrets required.

In Store mode the URL-rewriting is owned by flex-shield: the manifest is
requested with `?baseUrl=` and comes back with its asset URLs already pointing
under the DAM base path plus an `assetRewrites` map this service mirrors
(downloads each `from`, writes it at its `path`).

| Property | Default | Description |
|----------|---------|-------------|
| `httpTimeoutSeconds` | `30` | HTTP timeout for downloading assets |
| `damBasePath` | `/content/dam/ceros` | Root DAM folder for uploaded assets |
| `assetRewriteHost` | `https://ceros-dam.invalid` | Sentinel origin used to build the `baseUrl` sent to flex-shield's `?baseUrl=` rewrite. Must be a valid http(s) origin to pass server validation, but is stripped from the response so stored manifests keep root-relative DAM paths. The default uses the reserved `.invalid` TLD so it can never resolve. |

Example `.cfg.json`:

```json
{
    "httpTimeoutSeconds:Integer": 30,
    "damBasePath": "/content/dam/ceros",
    "assetRewriteHost": "https://ceros-dam.invalid"
}
```
