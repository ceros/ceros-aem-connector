# Releasing

How to cut a new release of **`ceros-aem-connector-all`** to Maven Central. The release is automated via the GitHub Actions workflow triggered by GitHub Releases tagged `release-X.Y.Z` — most of the work happens in CI. The manual steps below are the bookends: prep, draft, promote, and final publish on the Central Portal.

The workflow distinguishes draft vs. published GitHub Releases:

- **Draft release** → CI runs `mvn -P release verify -pl all -am` (signs artifacts locally; uploads nothing). Use this as a dry run.
- **Published release** → CI runs `mvn -P release deploy -pl all -am` (uploads signed artifacts to Central Portal staging).

For the one-time setup (Sonatype account, GPG key, POM metadata, release profile, CI secrets), see [MAVEN_CENTRAL_RELEASE.md](MAVEN_CENTRAL_RELEASE.md).

---

## Prerequisites

Confirm these are in place before starting:

- You have write access to the `ceros/ceros-aem-connector` repo and permission to push tags.
- The following repo secrets exist in GitHub Actions:
  - `CENTRAL_USERNAME` — Central Portal user token username
  - `CENTRAL_PASSWORD` — Central Portal user token password
  - `GPG_PRIVATE_KEY` — armored private key (`gpg --armor --export-secret-keys <KEY_ID>`)
  - `GPG_PASSPHRASE` — passphrase for the signing key
- You have an account on [central.sonatype.com](https://central.sonatype.com) with access to the `com.ceros` namespace (for the manual promotion step).

## 1. Pre-flight on `main`

- [ ] `main` is green in CI and contains everything you intend to ship.
- [ ] `CHANGELOG.md` has an entry for the new version with a dated release header.
- [ ] All four POMs (`pom.xml`, `core/pom.xml`, `ui.apps/pom.xml`, `ui.config/pom.xml`, `all/pom.xml`) are on the version you're about to release (no `-SNAPSHOT`).
- [ ] Local dry run passes:
  ```sh
  mvn -P release verify -pl all -am
  ```
  Inspect `all/target/` for `ceros-aem-connector-all-<version>.zip` and the matching `.asc` signature.

## 2. Bump the version (if needed)

If `main` is currently on a `-SNAPSHOT`, bump to the release version first.

```sh
mvn versions:set -DnewVersion=<X.Y.Z> -DprocessAllModules
mvn versions:commit
git commit -am "Release <X.Y.Z>"
git push origin main
```

## 3. Create a draft GitHub Release (dry run)

Create the Release as a **draft** with tag `release-<X.Y.Z>` targeting `main`. The workflow will run `mvn -P release verify -pl all -am` to confirm the artifact builds and signs cleanly without uploading anything.

```sh
gh release create release-<X.Y.Z> \
  --draft \
  --target main \
  --title "release-<X.Y.Z>" \
  --notes-file CHANGELOG.md
```

Follow the **Release** workflow run:

```sh
gh run watch
# or open: https://github.com/ceros/ceros-aem-connector/actions
```

If the dry run fails, fix forward on `main`, delete the draft release (`gh release delete release-<X.Y.Z>`), and recreate it.

## 4. Publish the release (real deploy)

Once the dry run is green, publish the draft. Publishing fires the `release: published` event and CI runs `mvn -P release deploy -pl all -am`, uploading signed artifacts to the Central Portal staging area.

```sh
gh release edit release-<X.Y.Z> --draft=false
```

Or click **Publish release** in the GitHub UI.

The workflow will:
1. Check out the tagged commit and import the GPG key.
2. Run `mvn -P release deploy -pl all -am`.
3. Upload the signed content-package zip to the Central Portal **staging** area.
4. Print a confirmation in the job summary.

If the deploy fails, fix forward — delete the release and tag (`gh release delete release-<X.Y.Z> --cleanup-tag`), land the fix on `main`, and start again at step 3.

## 5. Promote on Central Portal

Because we publish with `autoPublish=false`, the deployment lands in staging and waits for manual promotion.

1. Log into [central.sonatype.com](https://central.sonatype.com).
2. Open **Deployments** and find the staging entry matching your version (ID from the CI summary).
3. Verify validation passed (signatures, POM metadata, no errors).
4. Click **Publish** to promote to the public Maven Central index.

Once published, the version is **immutable** — there is no unpublish.

## 6. Verify the artifact

Indexing onto `repo1.maven.org` typically takes 15 minutes to a few hours (worst case 24h).

```sh
curl -I https://repo1.maven.org/maven2/com/ceros/ceros-aem-connector-all/<X.Y.Z>/ceros-aem-connector-all-<X.Y.Z>.zip
```

Spot check by adding the dependency to a scratch project and resolving it.

## 7. Post-release

- [ ] Create a GitHub Release from the tag, pasting the relevant `CHANGELOG.md` section as the body.
- [ ] Bump versions on `main` to the next `-SNAPSHOT`:
  ```sh
  mvn versions:set -DnewVersion=<X.Y.Z+1>-SNAPSHOT -DprocessAllModules
  mvn versions:commit
  git commit -am "Prepare next development iteration"
  git push origin main
  ```
- [ ] Open `CHANGELOG.md` with a new `## [Unreleased]` section at the top.

## Troubleshooting

- **GPG signing fails in CI**: check that `GPG_PRIVATE_KEY` is the *armored* export and that `GPG_PASSPHRASE` matches. The key must not be expired.
- **Central Portal rejects the upload**: usually a POM-metadata issue (`url`, `licenses`, `scm`, `developers`, `organization`). Re-validate locally with `mvn -P release verify -pl all -am` and compare against the rules at [central.sonatype.org/publish/requirements](https://central.sonatype.org/publish/requirements/).
- **Wrong version went out**: you cannot republish the same coordinates. Bump to the next patch version and release again.
