#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/verify-branding.sh
node <<'JS'
const fs = require('node:fs');
const path = require('node:path');
const root = 'app/build/outputs/apk/internal';
const metadata = JSON.parse(fs.readFileSync(`${root}/output-metadata.json`, 'utf8'));
if (metadata.elements.length !== 1) throw new Error('Expected exactly one universal APK');
const apk = metadata.elements[0];
if (!/^[0-9]+\.[0-9]+\.[0-9]+-debug$/.test(apk.versionName)) throw new Error('Unexpected version name');
if (metadata.applicationId !== 'com.kissedcode.chargingtile') throw new Error('Wrong application ID');
fs.mkdirSync('dist', {recursive: true});
fs.copyFileSync(path.join(root, apk.outputFile), `dist/charging-optimization-tile-${apk.versionName}.apk`);
fs.copyFileSync('app/build/outputs/mapping/internal/mapping.txt', 'dist/mapping.txt');
fs.writeFileSync('dist/build-metadata.json', JSON.stringify({
  applicationId: metadata.applicationId, versionCode: apk.versionCode, versionName: apk.versionName,
  commit: process.env.GITHUB_SHA || null
}, null, 2) + '\n');
JS
sha256sum dist/*.apk
