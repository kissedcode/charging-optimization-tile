#!/usr/bin/env bash
# Run once as a project IAM administrator with authenticated gcloud and gh.
# Never creates or downloads a Google service-account private key.
set -euo pipefail
PROJECT_ID="${1:?Usage: setup-oidc.sh FIREBASE_PROJECT_ID}"
REPO="kissedcode/charging-optimization-tile"
REPO_ID="1426125944"
OWNER_ID="18675884"
POOL="charging-tile-gh"
PROVIDER="github-main"
SA_ID="charging-tile-distributor"
SERVICE_ACCOUNT="$SA_ID@$PROJECT_ID.iam.gserviceaccount.com"
TOOLS=(gcloud)
if [[ "${SKIP_GITHUB_CONFIG:-0}" != "1" ]]; then TOOLS+=(gh); fi
for tool in "${TOOLS[@]}"; do
  command -v "$tool" >/dev/null || { echo "Install $tool first." >&2; exit 1; }
done
if [[ "${SKIP_GITHUB_CONFIG:-0}" != "1" ]]; then
  [[ "$(gh api "repos/$REPO" --jq .id)" == "$REPO_ID" ]] || {
    echo "Repository identity does not match the reviewed configuration." >&2; exit 1;
  }
fi
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
test -n "$PROJECT_NUMBER"
echo "Configuring App Distribution OIDC for $REPO in $PROJECT_ID."
gcloud services enable iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  cloudresourcemanager.googleapis.com firebaseappdistribution.googleapis.com \
  --project="$PROJECT_ID" --quiet
if ! gcloud iam service-accounts describe "$SERVICE_ACCOUNT" --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$SA_ID" --project="$PROJECT_ID" \
    --display-name="Charging Tile GitHub distribution" --quiet
fi
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SERVICE_ACCOUNT" \
  --role="roles/firebaseappdistro.admin" --condition=None --quiet >/dev/null
if ! gcloud iam workload-identity-pools describe "$POOL" \
  --project="$PROJECT_ID" --location=global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "$POOL" --project="$PROJECT_ID" \
    --location=global --display-name="Charging Tile GitHub" --quiet
fi
MAPPING="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.ref=assertion.ref,attribute.workflow_ref=assertion.workflow_ref,attribute.event_name=assertion.event_name"
CONDITION="assertion.repository_id == '$REPO_ID' && assertion.repository_owner_id == '$OWNER_ID' && assertion.ref == 'refs/heads/main' && assertion.workflow_ref == '$REPO/.github/workflows/distribute.yml@refs/heads/main' && assertion.event_name in ['push', 'workflow_dispatch']"
if gcloud iam workload-identity-pools providers describe "$PROVIDER" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER" \
    --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="$MAPPING" --attribute-condition="$CONDITION" --quiet
else
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" \
    --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="$MAPPING" --attribute-condition="$CONDITION" --quiet
fi
POOL_NAME="projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$POOL"
gcloud iam service-accounts add-iam-policy-binding "$SERVICE_ACCOUNT" --project="$PROJECT_ID" \
  --member="principalSet://iam.googleapis.com/$POOL_NAME/attribute.repository_id/$REPO_ID" \
  --role="roles/iam.workloadIdentityUser" --quiet >/dev/null
if [[ "${SKIP_GITHUB_CONFIG:-0}" != "1" ]]; then
  gh variable set FIREBASE_PROJECT_ID --repo "$REPO" --env firebase-distribution --body "$PROJECT_ID"
  gh variable set GCP_SERVICE_ACCOUNT --repo "$REPO" --env firebase-distribution --body "$SERVICE_ACCOUNT"
  gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --repo "$REPO" --env firebase-distribution \
    --body="$POOL_NAME/providers/$PROVIDER"
else
  printf 'FIREBASE_PROJECT_ID=%s\nGCP_SERVICE_ACCOUNT=%s\nGCP_WORKLOAD_IDENTITY_PROVIDER=%s\n' \
    "$PROJECT_ID" "$SERVICE_ACCOUNT" "$POOL_NAME/providers/$PROVIDER"
fi
echo "OIDC configured. Set FIREBASE_APP_ID after registering com.kissedcode.chargingtile."
