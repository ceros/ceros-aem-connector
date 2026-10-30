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
| `cerosOwnedDomains` | `ceros.site` | Apex domains trusted to serve manifests. A pasted URL is only fetched and injected when the resolved manifest host exactly equals — or is a dotted subdomain of — one of these. Look-alikes (`evilceros.site`, `ceros.site.evil.com`) are rejected. Production domains only; override per environment where needed. |
| `allowUntrustedManifestHost` | `false` | Skip the Ceros-owned domain whitelist (and the `x-flex-manifest` discovery step) and trust any host that passes the SSRF policy. Dev/test only (manifests served from localhost). Leave **off** in production. |

### Trusting pasted experience URLs

Pasted URLs are not trusted by default. Authors may paste **any** experience
URL — including a customer vanity domain — but the connector only ever fetches a
manifest, and injects the scripts it references, from a **Ceros-owned** host:

- A URL already on a Ceros-owned domain resolves to its manifest directly.
- A vanity domain is asked to advertise its canonical, Ceros-hosted manifest URL
  via the `x-flex-manifest` response header on the published page. The advertised URL must itself pass the
  Ceros-owned whitelist before it is fetched, so a spoofed header pointing
  off-Ceros is rejected.
- Anything that does not resolve to a Ceros-owned manifest is refused.

In production, leave `allowUntrustedManifestHost` off so this whitelist is
enforced. The connector ships **production-safe defaults only** and does not
bundle any dev relaxations. For local/dev — where experiences are served from
`localhost` or non-production environments — apply a run-mode OSGi config in
your **consuming project** (e.g. `config.author.sdk/com.ceros.services.impl.CerosManifestServiceImpl.cfg.json`)
that enables `allowHttpScheme`, `allowLocalAddresses` and `allowUntrustedManifestHost`
and adds your non-production Ceros manifest domains to `cerosOwnedDomains`:

```json
{
    "allowHttpScheme:Boolean": true,
    "allowLocalAddresses:Boolean": true,
    "allowUntrustedManifestHost:Boolean": true,
    "cerosOwnedDomains": ["ceros.site"]
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
| `flexApiVersion` | `2026-08-06-09-00` | Sent as the `x-ceros-api-version` header on every Flex API request. An empty value falls back to the default — the header is always sent. |
| `httpTimeoutSeconds` | `30` | HTTP timeout |

Example `.cfg.json`:

```json
{
    "flexApiKey": "YOUR_CEROS_API_KEY_HERE",
    "flexApiBaseUrl": "https://rest.ceros.com",
    "flexViewBaseUrl": "https://ceros.site",
    "flexApiVersion": "2026-08-06-09-00",
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

Example `.cfg.json`:

```json
{
    "httpTimeoutSeconds:Integer": 30,
    "damBasePath": "/content/dam/ceros"
}
```

---

## Delivery Service (`CerosFlexDeliveryServiceImpl`)

Renders the Ceros Flex component in its configured delivery mode.

| Property | Default | Description |
|----------|---------|-------------|
| `analyticsEnabled` | `false` (`true` on `publish`, but not the `preview` tier) | Lets experiences on this instance report to Ceros Analytics. When `false`, no component reports, whatever its **Ceros analytics tracking** checkbox says. |

Reporting is off unless a config turns it on, so an instance the connector's
run-mode configs don't reach never reports author traffic. The connector ships
a `config.publish` config that turns it on and a `config.preview` one that keeps
it off, because every visitor to author and preview is an author checking a
page. If you deploy `core` and `ui.apps` without `ui.config`, or use custom run
modes, set it on your publish instances yourself. Turn it off for any other
publish environment real visitors don't use, for example
`config.publish.stage/com.ceros.services.impl.CerosFlexDeliveryServiceImpl.cfg.json`:

```json
{
    "analyticsEnabled:Boolean": false
}
```

Where it is on, each component writes `data-ceros-analytics="enabled"` or
`"disabled"` on its embed container. A component reports only when its checkbox
is on, the page renders with `wcmmode=disabled`, and it uses Always Fetch,
Inline embed or Iframe embed. Store and HTML Import never report. A component
added before the checkbox existed keeps what it did before until an author
saves its dialog: an Iframe embed reports, Always Fetch and Inline embed don't.
