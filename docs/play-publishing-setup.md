# Play Store publishing setup

One-time setup that lets CI publish to Google Play. Until it is done,
`main.yml` and `release.yml` skip the publish/promote steps (they guard on the
`GCLOUD_*` repo vars) and only the debug APK is published to the `main-debug`
GitHub Release.

Once complete:

- **push to `main`** → signed release AAB is uploaded to the Play **internal**
  track (versionName `<base>-<sha>`).
- **push a `vX.Y.Z` tag** → the latest internal release is promoted to the
  **alpha** (closed testing) track.

The app is `org.hogel.tidytalk`. The commands below assume a dedicated Google
Cloud project named `tidytalk`; adjust the ids to taste.

## 1. Google Cloud: Workload Identity Federation + service account

CI authenticates with GitHub's OIDC token via Workload Identity Federation
(WIF) — no long-lived JSON key is stored. Run these once with an account that
can administer the project.

```bash
PROJECT=tidytalk
REPO=hogelog/tidytalk

gcloud config set project "$PROJECT"

# Enable the Play Developer API.
gcloud services enable androidpublisher.googleapis.com

# Service account CI impersonates to upload bundles.
gcloud iam service-accounts create tidytalk-publisher \
  --display-name="tidytalk Play publisher"

SA="tidytalk-publisher@${PROJECT}.iam.gserviceaccount.com"
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')

# WIF pool + GitHub OIDC provider, restricted to this repository.
gcloud iam workload-identity-pools create github-actions \
  --location=global --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc github-actions \
  --location=global --workload-identity-pool=github-actions \
  --display-name="GitHub Actions" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${REPO}'"

# Let the GitHub repo impersonate the service account.
gcloud iam service-accounts add-iam-policy-binding "$SA" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-actions/attribute.repository/${REPO}"

# Print the two values needed for the GitHub repo vars (step 4).
echo "GCLOUD_SERVICE_ACCOUNT=$SA"
echo "GCLOUD_WIF_PROVIDER=projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-actions/providers/github-actions"
```

## 2. Play Console: register the app and the first manual upload

The Play Developer API **cannot create an app or push its first bundle** — both
must be done by hand once.

1. In the Play Console, create the app for `org.hogel.tidytalk`.
2. Build a signed AAB locally and upload it to the **internal testing** track
   manually, then roll it out. (`./gradlew bundleRelease` with the release
   keystore env vars — see `app/build.gradle.kts`.)

After this first release exists, CI's `publishReleaseBundle` can add new
releases on the internal track.

## 3. Play Console: grant the service account access

Link the Google Cloud project and authorize the service account:

1. Play Console → **Users and permissions** → **Invite new users**.
2. Enter `tidytalk-publisher@tidytalk.iam.gserviceaccount.com`.
3. Grant app access to `org.hogel.tidytalk` with at least: **Release to testing
   tracks** and **Manage testing track releases** (Admin works too).

> If the Play Console account is linked to a different Google Cloud project, you
> can instead enable the Play Developer API there and create the service account
> in that project — the WIF binding in step 1 must then point at the same
> service account.

## 4. GitHub repo vars

The workflows enable publishing only when both vars are set:

```bash
gh variable set GCLOUD_SERVICE_ACCOUNT -R hogelog/tidytalk \
  -b "tidytalk-publisher@tidytalk.iam.gserviceaccount.com"
gh variable set GCLOUD_WIF_PROVIDER -R hogelog/tidytalk \
  -b "projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-actions/providers/github-actions"
```

The release keystore secrets (`RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) are
already configured and are used by both the local and CI builds.

## 5. Verify

Push any commit to `main` (or re-run the latest `main.yml`). The
**Authenticate to Google Cloud** and **Publish AAB to Play Store internal
track** steps should now run instead of being skipped, and a new release should
appear on the internal track in the Play Console.
