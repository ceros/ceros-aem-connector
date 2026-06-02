# Releasing

How to cut a new release of **`ceros-aem-connector-all`** to Maven Central. The release is automated via the GitHub Actions workflow in `.github/workflows/release.yml`. The flow:

- **Push to a `release/X.Y.Z` branch** → CI runs `mvn -P release verify -pl all -am` (signs artifacts locally; uploads nothing). This is the canonical dry run.
- **Manual run** via **Actions → Release → Run workflow** (`workflow_dispatch`) → same dry run against whatever ref you pick. Useful for re-running the dry run against `main`, a draft's target ref, or any commit.
- **Publish a GitHub Release tagged `release-X.Y.Z`** → CI runs `mvn -P release deploy -pl all -am` (uploads signed artifacts to Central Portal staging).

GitHub Releases marked as **drafts do not trigger any workflow events** — to dry-run a draft, use the `workflow_dispatch` manual run against the draft's target ref.

For one-time setup (Sonatype account, GPG key, POM metadata, release profile, CI secrets), see [MAVEN_CENTRAL_RELEASE.md](MAVEN_CENTRAL_RELEASE.md).

---

## Prerequisites

Confirm these are in place before starting:

- You have write access to the `ceros/ceros-aem-connector` repo and permission to push branches and create releases.
- The `maven-central` GitHub Environment has these four secrets:
  - `CENTRAL_USERNAME` — Central Portal user token username
  - `CENTRAL_PASSWORD` — Central Portal user token password
  - `GPG_PRIVATE_KEY` — armored private key (`gpg --armor --export-secret-keys <KEY_ID>`)
  - `GPG_PASSPHRASE` — passphrase for the signing key
- You have an account on [central.sonatype.com](https://central.sonatype.com) with access to the `com.ceros` namespace (for the manual promotion step).

## 1. Pre-flight on `main`

- [ ] `main` is green in CI and contains everything you intend to ship.
- [ ] `CHANGELOG.md` has an entry for the new version with a dated release header.
- [ ] Optional local sanity check:
  ```sh
  mvn -P release verify -pl all -am
  ```
  Inspect `all/target/` for `ceros-aem-connector-all-<version>.zip` and the matching `.asc` signature.

## 2. Cut a `release/X.Y.Z` branch with the release version

Branch from `main`, set the POM version (no `-SNAPSHOT`), commit, and push.

```sh
git checkout -b release/<X.Y.Z>
mvn versions:set -DnewVersion=<X.Y.Z> -DprocessAllModules
mvn versions:commit
git commit -am "Release <X.Y.Z>"
git push -u origin release/<X.Y.Z>
```

The push fires the **Release** workflow in dry-run mode. Watch it:

```sh
gh run watch
# or: https://github.com/ceros/ceros-aem-connector/actions
```

The dry-run workflow:
1. Validates the branch name matches `release/X.Y.Z`.
2. Checks out the branch and imports the GPG key.
3. Verifies the POM version matches the branch.
4. Runs `mvn -P release verify -pl all -am` — produces a signed content-package zip in `all/target/`.

If it fails, fix forward on the branch and push again.

## 3. Publish the GitHub Release (real deploy)

Once the dry run is green, create a published GitHub Release with tag `release-<X.Y.Z>` against the release branch. **Do not use draft** — drafts don't fire workflow events.

```sh
gh release create release-<X.Y.Z> \
  --target release/<X.Y.Z> \
  --title "release-<X.Y.Z>" \
  --notes-file CHANGELOG.md
```

Or via the UI: **Releases → Draft a new release** with the tag, target the release branch, and click **Publish release** (skip "Save draft").

Publishing fires the `release: published` event and CI runs `mvn -P release deploy -pl all -am`, uploading signed artifacts to the Central Portal staging area.

The deploy workflow:
1. Validates the tag matches `release-X.Y.Z`.
2. Checks out the tagged commit and imports the GPG key.
3. Verifies the POM version matches the tag.
4. Runs `mvn -P release deploy -pl all -am`.
5. Prints a confirmation in the job summary.

If the deploy fails, fix forward — delete the release and tag (`gh release delete release-<X.Y.Z> --cleanup-tag`), push more commits to the release branch, and start step 3 again.

## 4. Promote on Central Portal

Because we publish with `autoPublish=false`, the deployment lands in staging and waits for manual promotion.

1. Log into [central.sonatype.com](https://central.sonatype.com).
2. Open **Deployments** and find the staging entry matching your version.
3. Verify validation passed (signatures, POM metadata, no errors).
4. Click **Publish** to promote to the public Maven Central index.

Once published, the version is **immutable** — there is no unpublish.

## 5. Verify the artifact

Indexing onto `repo1.maven.org` typically takes 15 minutes to a few hours (worst case 24h).

```sh
curl -I https://repo1.maven.org/maven2/com/ceros/ceros-aem-connector-all/<X.Y.Z>/ceros-aem-connector-all-<X.Y.Z>.zip
```

Spot check by adding the dependency to a scratch project and resolving it.

## 6. Post-release

- [ ] Merge the `release/<X.Y.Z>` branch back into `main` (or fast-forward if no commits diverged).
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
- **Draft release didn't trigger the workflow**: by design — GitHub does not fire `release` events for drafts. Use a `release/X.Y.Z` branch for the dry run instead.
