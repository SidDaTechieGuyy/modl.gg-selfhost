/* global fetch */
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import process from 'node:process';
import console from 'node:console';
import { URL } from 'node:url';
import { resolve } from 'node:path';
const base = process.env.E2E_BASE_URL || 'http://localhost:8091';
const origin = process.env.E2E_ORIGIN || 'http://localhost:8091';
const credentials = { identifier: 'testowner', password: 'Modl-test-password-2026!' };
let cookie = '';
async function api(path, method = 'GET', body, extraHeaders = {}) {
 const response = await fetch(base + '/api' + path, { method,
  headers: { Origin: origin, Accept: 'application/json', Cookie: cookie, ...extraHeaders,
    ...(body === undefined ? {} : {'Content-Type':'application/json'}) },
  body: body === undefined ? undefined : JSON.stringify(body) });
 const text = await response.text();
 assert.ok(response.ok, `${path}: ${response.status}: ${text}`);
 return { data: JSON.parse(text), response };
}
async function main() {
 assert.equal(process.env.E2E_DISPOSABLE, '1', 'Run only against an explicitly disposable instance');
 const status = await api('/v1/public/registration/status');
 const auth = status.data.registrationOpen
  ? await api('/v1/public/registration', 'POST', {username:'testowner',password:credentials.password,serverName:'Replay verification'})
  : await api('/v1/panel/auth/login','POST',credentials);
 cookie = auth.response.headers.getSetCookie().find(v=>v.startsWith('MODL_SESSION=')).split(';')[0];
 const key = (await api('/v1/panel/settings/api-keys/default/reveal')).data.apiKey;
 const bytes = await readFile(resolve('tests/fixtures/replay.modlreplay'));
 const upload = (await api('/v1/minecraft/replays/upload','POST',{mcVersion:'1.21.4',fileSize:bytes.length}, {'X-API-Key':key})).data;
 const address = new URL(upload.uploadUrl);
 const put = await fetch(base + address.pathname + address.search,{method:'PUT',body:bytes,headers:upload.requiredHeaders});
 assert.equal(put.status,204);
 await api('/v1/minecraft/replays/confirm/' + upload.replayId,'POST',undefined,{'X-API-Key':key});
 const metadata = (await api('/v1/public/replays/' + upload.replayId)).data;
 assert.equal(metadata.status,'COMPLETE');
 assert.equal(Number(metadata.fileSize),bytes.length);
 const download = new URL(metadata.replayUrl);
 const file = await fetch(base + download.pathname + download.search);
 assert.equal(file.status,200);
 assert.deepEqual(new Uint8Array(await file.arrayBuffer()),new Uint8Array(bytes));
 const range = await fetch(base + download.pathname + download.search,{headers:{Range:'bytes=0-4'}});
 assert.equal(range.status,206);
 assert.equal((await range.arrayBuffer()).byteLength,5);
 const listing = (await api('/v1/panel/storage/files')).data;
 assert.ok(listing.files.some(f=>f.key.includes('/replays/') && Number(f.size)===bytes.length));
 const retention = (await api('/v1/panel/settings/replay-retention')).data;
 assert.ok(retention.data && retention.data.days > 0);
 console.log('PASS: plugin replay upload, local confirmation, public metadata, exact download, range requests, file listing and retention settings');
 console.log(`Viewer: ${origin}/replay?id=${upload.replayId}`);
}
main().catch(error=>{console.error(error);process.exitCode=1;});
