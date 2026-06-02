# Maven Central Release Plan

Checklist to publish **`ceros-aem-connector-all`** (the content-package) to Maven Central. Customer projects
embed it as a `<type>zip</type>` dependency / subPackage in their Cloud Manager pipelines.

Adobe's Core Components follow exactly this pattern (`com.adobe.cq:core.wcm.components.all`), so
the precedent is well-established and validators handle content-package artifacts.

The other modules (`core`, `ui.apps`, `ui.config`) are internal building blocks of `all` and
are **not** published — they're already embedded inside the `all` content-package zip.

---

## 0. Decide before starting

- [x] **Confirm consumer audience.** All customers run Cloud Manager pipelines and want to embed the connector via Maven dependency — Central is the right channel.
- [x] **Group ID: `com.ceros`** — keeping it. Requires verifying ownership of `ceros.com` via DNS TXT record at [central.sonatype.com](https://central.sonatype.com).
- [ ] **Pick version strategy** (semver, current is `0.0.1`). Once published, versions on Central are immutable.

## 1. Sonatype / Central Portal setup

- [ ] Create account at [central.sonatype.com](https://central.sonatype.com).
- [ ] Register namespace (`com.ceros` or `io.github.ceros`) and complete verification.
- [ ] Generate a Central Portal user token (Account → Generate User Token) — store `username` and `password` in CI secrets.
- [ ] Generate GPG signing key (`gpg --gen-key`); publish the public key to `keys.openpgp.org` and a backup keyserver.
- [ ] Export GPG private key (`gpg --armor --export-secret-keys <KEY_ID>`); store in CI secret `GPG_PRIVATE_KEY` plus `GPG_PASSPHRASE`.

## 2. Fix existing repo issues

- [x] **Fix parent version mismatch in `ui.apps/pom.xml`** — all POMs now on `0.0.1`.
- [x] Check `ui.config/pom.xml` and `all/pom.xml` for the same parent-version drift — both reconciled to `0.0.1`.
- [x] **Delete `pom.xml.versionsBackup`** at repo root — deleted. `.gitignore` already had `pom.xml.versionsBackup` on line 67.
- [x] **Remove or fill in empty `Bundle-DocURL:`** — filled in with `https://github.com/ceros/ceros-aem-connector` in parent POM bnd config.
- [x] Verify `LICENSE` (BSD 3-Clause) is referenced consistently — `LICENSE`, `README.md`, and this doc all aligned.

## 3. Add required POM metadata (parent `pom.xml`)

All five blocks were missing; all five are now in place in the parent POM.

- [x] `<url>` — `https://github.com/ceros/ceros-aem-connector`
- [x] `<licenses>` — BSD 3-Clause, distribution `repo`
- [x] `<scm>` — `connection`, `developerConnection`, `url`
- [x] `<developers>` — single entry for Ceros Engineering (dev@ceros.com)
- [x] `<organization>` — Ceros, Inc. / https://www.ceros.com

## 4. Add release profile for signing / publishing

In parent `pom.xml`, add a `<profile id="release">` containing:

- [x] `maven-gpg-plugin` 3.2.4 — signs the content-package zip + POM in the `verify` phase.
- [x] `central-publishing-maven-plugin` 0.5.0 (Sonatype, not the deprecated `nexus-staging-maven-plugin`) with `<publishingServerId>central</publishingServerId>` and `<autoPublish>false</autoPublish>` for manual promotion first time.

**Content-package specifics**: `-sources.jar` and `-javadoc.jar` are **not required** for `content-package` / `zip` artifacts on Central — validators only enforce them for `jar` packaging. Adobe Core Components publishes its `.all` package this way. No source/javadoc plugins needed.

## 5. Skip the non-publishable modules

For `core/pom.xml`, `ui.apps/pom.xml`, `ui.config/pom.xml`:

- [x] Add `<skipPublishing>true</skipPublishing>` under the `central-publishing-maven-plugin` configuration in each module — applied via a `release` profile override in `core/pom.xml`, `ui.apps/pom.xml`, and `ui.config/pom.xml`.

## 6. Settings + CI wiring

- [x] Add `~/.m2/settings.xml` (or CI equivalent) with a `<server id="central">` entry containing the Central Portal token credentials — handled in CI by `actions/setup-java` with `server-id: central`.
- [x] Add GitHub Actions release workflow that:
  - [x] Triggers on GitHub Release creation with tag matching `release-X.Y.Z` (draft = dry run, published = real deploy).
  - [x] Imports GPG private key from secret.
  - [x] Runs `mvn -P release deploy -pl all -am` (deploys `all` and builds its dependencies).
  - [x] Reports outcome in the job summary.
- [x] Add a `RELEASING.md` documenting the manual steps (draft, publish, monitor CI, promote staging on Central Portal).

## 7. Dry run + first publish

- [ ] Run `mvn -P release verify -pl all -am` locally — confirms the content-package zip + GPG sigs are produced.
- [ ] Inspect `all/target/` for: `ceros-aem-connector-all-0.0.1.zip` and `.asc` next to it (plus signed POM).
- [ ] First publish: `mvn -P release deploy -pl all -am`. With `autoPublish=false`, it lands in Central Portal staging.
- [ ] Log into Central Portal, validate the staged deployment, manually promote to "Published".
- [ ] Verify the artifact appears at `https://repo1.maven.org/maven2/com/ceros/ceros-aem-connector-all/0.0.1/` (24h indexing delay possible).

## 8. Post-release hygiene

- [x] Add `CHANGELOG.md` documenting `0.0.1` as the initial release.
- [ ] Cut the GitHub Release with tag `release-0.0.1` (draft for dry run, then publish).
- [ ] Update README with the Maven coordinates and an example `<subPackage>` snippet so customers know how to embed in their Cloud Manager build.
- [ ] Bump version to next `-SNAPSHOT` for ongoing development.
