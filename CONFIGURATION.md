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

## Manifest Service (`CerosManifestServiceImpl`)

Fetches and parses Ceros experience manifests from public URLs.

| Property | Default | Description |
|----------|---------|-------------|
| `httpTimeoutSeconds` | `30` | HTTP timeout for fetching manifests |
| `allowHttpScheme` | `false` | Accept `http://` manifest URLs in addition to `https://`. Dev/test only. |
| `allowLocalAddresses` | `false` | Accept manifest URLs whose host is an IP literal or `localhost` alias. Dev/test only (SSRF guard). |
| `cerosOwnedDomains` | `ceros.com`, `ceros.site` | Apex domains trusted to serve manifests. A pasted URL is only fetched and injected when the resolved manifest host exactly equals — or is a dotted subdomain of — one of these. Look-alikes (`evilceros.com`, `ceros.com.evil.com`) are rejected. **Production domains only** — non-prod TLDs (`cerosdev.site`, `cerosstage.site`) are intentionally excluded so customer installs never reference internal environments; add them here for local/dev testing (the author-SDK config does). |
| `allowUntrustedManifestHost` | `false` | Skip the Ceros-owned domain whitelist (and the `x-flex-manifest` discovery step) and trust any host that passes the SSRF policy. Dev/test only (manifests served from localhost). Leave **off** in production. |

### Trusting pasted experience URLs

Pasted URLs are not trusted by default. Authors may paste **any** experience
URL — including a customer vanity domain — but the connector only ever fetches a
manifest, and injects the scripts it references, from a **Ceros-owned** host:

- A URL already on a Ceros-owned domain resolves to its manifest directly.
- A vanity domain is asked to advertise its canonical, Ceros-hosted manifest URL
  via the [`x-flex-manifest`](https://github.com/ceros/ceros-spark/pull/9861)
  response header on the published page. The advertised URL must itself pass the
  Ceros-owned whitelist before it is fetched, so a spoofed header pointing
  off-Ceros is rejected.
- Anything that does not resolve to a Ceros-owned manifest is refused.

In production, leave `allowUntrustedManifestHost` off so this whitelist is
enforced. The connector ships **production-safe defaults only** and does not
bundle any dev relaxations. For local/dev — where experiences are served from
`localhost` or non-prod environments — apply a run-mode OSGi config in your
**consuming project** (e.g. `config.author.sdk/com.ceros.services.impl.CerosManifestServiceImpl.cfg.json`)
that enables `allowHttpScheme`, `allowLocalAddresses` and `allowUntrustedManifestHost`
and adds `cerosdev.site` / `cerosstage.site` to `cerosOwnedDomains`:

```json
{
    "allowHttpScheme:Boolean": true,
    "allowLocalAddresses:Boolean": true,
    "allowUntrustedManifestHost:Boolean": true,
    "cerosOwnedDomains": ["ceros.com", "ceros.site", "cerosdev.site", "cerosstage.site"]
}
```

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

Downloads manifest-referenced assets and uploads them to AEM DAM. Used by
**Store** mode. All properties have sensible defaults — no secrets required.

| Property | Default | Description |
|----------|---------|-------------|
| `httpTimeoutSeconds` | `30` | HTTP timeout for downloading assets |
| `damBasePath` | `/content/dam/ceros` | Root DAM folder for uploaded assets |
| `mediaCdnBaseUrl` | `https://media.cdn.ceros.site/` | Base URL for media assets in HTML |

Example `.cfg.json`:

```json
{
    "httpTimeoutSeconds:Integer": 30,
    "damBasePath": "/content/dam/ceros",
    "mediaCdnBaseUrl": "https://media.cdn.ceros.site/"
}
```
