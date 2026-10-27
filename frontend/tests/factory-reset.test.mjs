/* global fetch, AbortSignal */
import assert from 'node:assert/strict';
import process from 'node:process';
import console from 'node:console';
import { setTimeout } from 'node:timers/promises';
import { URL } from 'node:url';

const base = process.env.E2E_BASE_URL || 'http://localhost:8091';
const origin = process.env.E2E_ORIGIN || 'http://localhost:8091';
let cookie = '';
async function api(path, method = 'GET', body, expected = 200) {
  const response = await fetch(base + '/api' + path, { method, headers: {
    Origin: origin, Accept: 'application/json', Cookie: cookie,
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
  }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000) });
  const text = await response.text();
  assert.equal(response.status, expected, `${path}: ${text}`);
  return { data: text ? JSON.parse(text) : null, response };
}
async function main() {
  assert.equal(process.env.E2E_DISPOSABLE, '1', 'Factory reset tests require an explicitly disposable instance');
  assert.equal((await api('/v1/public/registration/status')).data.registrationOpen, true, 'Requires a fresh disposable instance');
  const password = 'Modl-test-password-2026!';
  const owner = { username: 'testowner', password, serverName: 'Reset test' };
  const auth = await api('/v1/public/registration', 'POST', owner, 201);
  cookie = auth.response.headers.getSetCookie().find(value => value.startsWith('MODL_SESSION=')).split(';')[0];
  assert.equal((await api('/v1/panel/settings/factory-reset')).data.owner, true);
  const ownerCookie = cookie;
  cookie = '';
  const unauth = await fetch(base + '/api/v1/panel/settings/factory-reset', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify({ password, confirmation: 'RESET EVERYTHING' }) });
  assert.ok([401, 403].includes(unauth.status));
  cookie = ownerCookie;
  await api('/v1/panel/settings/factory-reset', 'POST', { password, confirmation: 'reset everything' }, 400);
  await api('/v1/panel/settings/factory-reset', 'POST', { password: 'incorrect', confirmation: 'RESET EVERYTHING' }, 401);
  assert.equal((await api('/v1/public/registration/status')).data.registrationOpen, false);
  const signed = (await api('/v1/panel/media/presign', 'POST', { fileName: 'reset.txt', uploadType: 'article', contentType: 'text/plain', fileSize: '4' })).data;
  const upload = new URL(signed.presignedUrl);
  assert.equal((await fetch(base + upload.pathname + upload.search, { method: 'PUT', headers: signed.requiredHeaders, body: 'test' })).status, 204);
  const file = (await api('/v1/panel/media/confirm', 'POST', { key: signed.key })).data;
  assert.equal((await api('/v1/panel/storage/files')).data.files.length, 1);
  const retention = (await api('/v1/panel/settings/replay-retention')).data;
  await api('/v1/panel/settings/replay-retention', 'PATCH', { expectedVersion: Number(retention._meta?.version ?? 0), days: 29 });
  await api('/v1/panel/settings/factory-reset', 'POST', { password, confirmation: 'RESET EVERYTHING' }, 202);
  let opened = false;
  for (let attempt = 0; attempt < 90; attempt++) {
    await setTimeout(2000);
    try {
      const response = await fetch(base + '/api/v1/public/registration/status', { signal: AbortSignal.timeout(3000) });
      if (response.ok && (await response.json()).registrationOpen === true) { opened = true; break; }
    } catch { /* Restart temporarily disconnects the gateway. */ }
  }
  assert.ok(opened, 'The backend must restart and reopen registration');
  cookie = '';
  await api('/v1/panel/auth/login', 'POST', { identifier: owner.username, password }, 401);
  const next = await api('/v1/public/registration', 'POST', { ...owner, username: 'newowner', serverName: 'Fresh instance' }, 201);
  cookie = next.response.headers.getSetCookie().find(value => value.startsWith('MODL_SESSION=')).split(';')[0];
  const download = new URL(file.url);
  assert.equal((await fetch(base + download.pathname + download.search)).status, 404, 'Old uploads are removed');
  const oldSession = await fetch(base + '/api/v1/panel/settings/general', { headers: { Cookie: ownerCookie, Origin: origin } });
  assert.ok([401, 403].includes(oldSession.status), 'Old owner session must be revoked');
  assert.equal((await api('/v1/public/registration/status')).data.registrationOpen, false);
  assert.equal((await api('/v1/panel/storage/files')).data.files.length, 0);
  assert.notEqual((await api('/v1/panel/settings/replay-retention')).data.data.days, 29);
  console.log('PASS: reset rejects anonymous users, incorrect phrase/password; restart clears uploads, settings, credentials and sessions; first-owner registration works again');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
