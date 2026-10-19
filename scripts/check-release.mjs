// Uses the Google auth library bundled with the pinned Firebase CLI installation.
// Nothing here loads Google credentials from a checked-in file.
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import path from 'node:path';
const globalRoot = execFileSync('npm', ['root', '-g'], {encoding: 'utf8'}).trim();
const require = createRequire(path.join(globalRoot, 'firebase-tools/package.json'));
const {GoogleAuth} = require('google-auth-library');
const appId = process.env.FIREBASE_APP_ID;
const projectNumber = /^1:(\d+):android:[a-z0-9]+$/i.exec(appId || '')?.[1];
if (!projectNumber) throw new Error('Invalid Android Firebase App ID');
const build = JSON.parse(fs.readFileSync('dist/build-metadata.json', 'utf8'));
const auth = new GoogleAuth({scopes: ['https://www.googleapis.com/auth/cloud-platform']});
const client = await auth.getClient();
let pageToken;
do {
  const url = `https://firebaseappdistribution.googleapis.com/v1/projects/${projectNumber}/apps/${encodeURIComponent(appId)}/releases`;
  const {data} = await client.request({url, params: {pageSize: 100, ...(pageToken ? {pageToken} : {})}});
  for (const release of data.releases || []) {
    const code = Number(release.buildVersion);
    if (Number.isFinite(code) && code >= build.versionCode) {
      throw new Error(`VersionCode ${build.versionCode} is not newer than an existing release (${code}). Bump both versions before distributing.`);
    }
  }
  pageToken = data.nextPageToken;
} while (pageToken);
console.log(`Version ${build.versionName} (${build.versionCode}) is newer than existing releases.`);
