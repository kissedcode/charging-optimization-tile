// Uses the Google auth library bundled with the pinned Firebase CLI installation.
// Nothing here loads Google credentials from a checked-in file.
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import path from 'node:path';
class ReleasePolicyError extends Error {}

async function main() {
  const globalRoot = execFileSync('npm', ['root', '-g'], {encoding: 'utf8'}).trim();
  const require = createRequire(path.join(globalRoot, 'firebase-tools/package.json'));
  const {GoogleAuth} = require('google-auth-library');
  const appId = process.env.FIREBASE_APP_ID;
  const projectNumber = /^1:(\d+):android:[a-z0-9]+$/i.exec(appId || '')?.[1];
  if (!projectNumber) throw new ReleasePolicyError('Invalid Android Firebase App ID');
  const build = JSON.parse(fs.readFileSync('dist/build-metadata.json', 'utf8'));
  const auth = new GoogleAuth({scopes: ['https://www.googleapis.com/auth/cloud-platform']});
  const client = await auth.getClient();
  let pageToken;
  do {
    const url = `https://firebaseappdistribution.googleapis.com/v1/projects/${projectNumber}/apps/${encodeURIComponent(appId)}/releases`;
    let data;
    try {
      ({data} = await client.request({url, params: {pageSize: 100, ...(pageToken ? {pageToken} : {})}}));
    } catch (error) {
      // A registered Android app has no App Distribution resource until its first upload.
      // Do not interpret arbitrary missing/inaccessible apps as an empty release history.
      if (error?.response?.status !== 404 || pageToken || build.versionCode !== 1) throw error;
      const registrationUrl = `https://firebase.googleapis.com/v1beta1/projects/${projectNumber}/androidApps/${encodeURIComponent(appId)}`;
      const {data: app} = await client.request({url: registrationUrl});
      if (app.appId !== appId || app.packageName !== build.applicationId ||
          app.projectId !== process.env.FIREBASE_PROJECT_ID || app.state !== 'ACTIVE') {
        throw new ReleasePolicyError('Firebase app registration does not match the signed APK.');
      }
      console.log('Registered Android app verified. Preparing its first App Distribution release (versionCode 1).');
      return;
    }
    for (const release of data.releases || []) {
      const code = Number(release.buildVersion);
      if (Number.isFinite(code) && code >= build.versionCode) {
        throw new ReleasePolicyError(`VersionCode ${build.versionCode} is not newer than an existing release (${code}). Bump both versions before distributing.`);
      }
    }
    pageToken = data.nextPageToken;
  } while (pageToken);
  console.log(`Version ${build.versionName} (${build.versionCode}) is newer than existing releases.`);
}

main().catch(error => {
  // Gaxios errors can include request headers and credential response bodies.
  // Only our own fixed-policy messages or a numeric HTTP status may reach CI logs.
  if (error instanceof ReleasePolicyError) {
    console.error(error.message);
  } else {
    const status = Number(error?.response?.status);
    console.error(`Release check failed${Number.isFinite(status) ? ` (HTTP ${status})` : ''}. Check OIDC/IAM configuration and Firebase CLI dependencies. Request details are intentionally not logged.`);
  }
  process.exitCode = 1;
});
