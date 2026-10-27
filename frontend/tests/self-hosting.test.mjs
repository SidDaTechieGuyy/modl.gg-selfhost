/* global fetch */
/** Run only against a disposable Compose project. Creates an owner and one upload. */
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import { Buffer } from 'node:buffer';
import process from 'node:process';
import console from 'node:console';
import { URL } from 'node:url';
import { setTimeout, clearTimeout } from 'node:timers';
import { setTimeout as delay } from 'node:timers/promises';
import { create, fromBinary, toBinary } from '@bufbuild/protobuf';
import { ClientHelloSchema, ClientKind, RealtimeEnvelopeSchema, Topic } from '@modl-gg/proto/modl/v1/realtime_pb.ts';

const base = process.env.E2E_BASE_URL || 'http://localhost:8080';
const origin = process.env.E2E_ORIGIN || 'http://localhost:8080';
const username = 'testowner';
const email = 'owner@example.test';
const password = process.env.E2E_PASSWORD || 'Modl-test-password-2026!';
let cookies = '';
const unwrap = value => value;
async function request(path, { method = 'GET', body, authenticated = true, headers = {} } = {}) {
  for (let attempt = 0; ; attempt++) {
    const response = await fetch(base + path, {
    method, headers: { Origin: origin, Accept: 'application/json',
      ...(authenticated && cookies ? { Cookie: cookies } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
    if (response.status !== 429 || attempt >= 4) return response;
    const retryAfter = Number(response.headers.get('Retry-After') || 3);
    await response.arrayBuffer();
    await delay(Math.max(1, Math.min(60, retryAfter)) * 1000);
  }
}
async function json(response, status = 200) {
  const text = await response.text();
  assert.equal(response.status, status, `Unexpected status: ${response.status}: ${text}`);
  return unwrap(JSON.parse(text));
}
function saveSession(response) {
  const session = response.headers.getSetCookie().find(value => value.startsWith('MODL_SESSION='));
  assert.ok(session, 'Authentication must set a session cookie');
  assert.match(session, /HttpOnly/i);
  cookies = session.split(';')[0];
}
function localPath(url) { const parsed = new URL(url); return parsed.pathname + parsed.search; }

async function websocket(allowedOrigin, expectedStatus, plugin) {
  return new Promise((resolve, reject) => {
    const url = new URL('/api/v1/realtime/ws', base);
    const timeout = setTimeout(() => { req.destroy(); reject(new Error('WebSocket handshake timed out')); }, 15000);
    const finish = error => { clearTimeout(timeout); if (error) reject(error); else resolve(); };
    const req = http.request(url, { headers: { Connection: 'Upgrade', Upgrade: 'websocket',
      'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': randomBytes(16).toString('base64'),
      ...(plugin ? { 'X-API-Key': plugin.apiKey } : { Origin: allowedOrigin, Cookie: cookies }) } });
    req.on('error', finish);
    req.on('response', response => {
      response.resume();
      try { assert.equal(response.statusCode, expectedStatus); finish(); } catch (e) { finish(e); }
    });
    req.on('upgrade', (response, socket, head) => {
      if (expectedStatus !== 101) { socket.destroy(); finish(new Error('Untrusted origin upgraded')); return; }
      const hello = toBinary(RealtimeEnvelopeSchema, create(RealtimeEnvelopeSchema, {
        protocolVersion: 1, payload: { case: 'clientHello', value: create(ClientHelloSchema, {
          clientKind: plugin ? ClientKind.MINECRAFT_PLUGIN : ClientKind.PANEL, protocolVersion: 1,
          supportedTopics: plugin ? [] : [Topic.PANEL_DASHBOARD],
          ...(plugin ? { serverInstanceId: plugin.instanceId, serverName: 'Synthetic plugin' } : {}),
        }) },
      }));
      assert.ok(hello.length < 126);
      const mask = randomBytes(4);
      const masked = Buffer.from(hello).map((byte, i) => byte ^ mask[i % 4]);
      socket.write(Buffer.concat([Buffer.from([0x82, 0x80 | hello.length]), mask, masked]));
      let buffered = head;
      socket.on('error', finish);
      socket.on('data', chunk => {
        buffered = Buffer.concat([buffered, chunk]);
        if (buffered.length < 2) return;
        let length = buffered[1] & 127, offset = 2;
        if (length === 126) { if (buffered.length < 4) return; length = buffered.readUInt16BE(2); offset = 4; }
        if (length === 127) { socket.destroy(); finish(new Error('Unexpected large server hello')); return; }
        if (buffered.length < offset + length) return;
        try {
          assert.equal(buffered[0] & 15, 2, 'Server must send binary protobuf');
          const envelope = fromBinary(RealtimeEnvelopeSchema, buffered.subarray(offset, offset + length));
          assert.equal(envelope.payload.case, 'serverHello', `Realtime rejected session: ${envelope.payload.case}`);
          socket.destroy(); finish();
        } catch (e) { socket.destroy(); finish(e); }
      });
    });
    req.end();
  });
}

async function main() {
  assert.equal(process.env.E2E_DISPOSABLE, '1', 'Set E2E_DISPOSABLE=1 only for a disposable instance');
  const status = await json(await request('/api/v1/public/registration/status', { authenticated: false }));
  if (status.registrationOpen) {
    const payload = { username, password, serverName: 'Verification server' };
    await json(await request('/api/v1/public/registration', { method: 'POST', body: { ...payload, password: '1234567' }, authenticated: false }), 400);
    const responses = await Promise.all([request('/api/v1/public/registration', { method: 'POST', body: payload, authenticated: false }),
      request('/api/v1/public/registration', { method: 'POST', body: payload, authenticated: false })]);
    assert.deepEqual(responses.map(r => r.status).sort(), [201, 409], 'Exactly one racing registration must succeed');
    saveSession(responses.find(r => r.status === 201));
  } else {
    const login = await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: username, password } });
    await json(login); saveSession(login);
  }
  assert.equal((await json(await request('/api/v1/public/registration/status'))).registrationOpen, false);
  await json(await request('/api/v1/public/registration', { method: 'POST', body: { username, password, serverName: 'Other' } }), 409);
  await json(await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: username, password: 'incorrect-password' } }), 401);
  const me = await json(await request('/api/v1/panel/auth/me'));
  assert.equal(me.username, username);
  await json(await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: email, password }, authenticated: false }), 401);
  const preflight = await request('/api/v1/panel/auth/me', { method: 'OPTIONS', headers: { 'Access-Control-Request-Method': 'GET' } });
  assert.ok(preflight.ok);
  assert.equal(preflight.headers.get('access-control-allow-origin'), origin);
  assert.equal(preflight.headers.get('access-control-allow-credentials'), 'true');
  const untrusted = 'https://untrusted.invalid';
  const denied = await request('/api/v1/panel/auth/me', { method: 'OPTIONS', headers: { Origin: untrusted, 'Access-Control-Request-Method': 'GET' } });
  assert.equal(denied.status, 403);
  await websocket(origin, 101);
  await websocket(untrusted, 403);
  const aiUsage = await json(await request('/api/v1/panel/settings/ai-usage'));
  assert.equal(aiUsage.usage.totalUsed, 0);
  assert.equal(aiUsage.providerAvailable, false);
  assert.equal(aiUsage.usage.trackingAvailable, true);
  const pluginKey = await json(await request('/api/v1/panel/settings/api-keys/default/reveal'));
  const startup = await json(await fetch(base + '/v2/minecraft/startup', { method: 'POST',
    headers: { 'X-API-Key': pluginKey.apiKey, 'Content-Type': 'application/json' }, body: JSON.stringify({
      serverVersion: '1.21.4', platformType: 'PAPER', pluginVersion: 'test', maxPlayers: 20, serverName: 'Synthetic plugin',
    }) }));
  assert.equal(startup.panelUrl, origin);
  assert.equal(startup.realtimeUrl, origin.replace(/^http/, 'ws') + '/api/v1/realtime/ws');
  await websocket(null, 101, { apiKey: pluginKey.apiKey, instanceId: startup.serverInstanceId });
  console.log('PASS: automatic BASE_URL origin policy, native /v2 plugin startup, and persisted AI usage endpoint');
  console.log('PASS: owner registration closure, authentication, HTTP origins and authenticated realtime');

  const data = Buffer.from('Self-hosted persistent upload verification.\n');
  const presign = await json(await request('/api/v1/panel/media/presign', { method: 'POST', body: {
    uploadType: 'article', fileName: 'verification.txt', contentType: 'text/plain', fileSize: String(data.length),
  } }));
  const upload = await fetch(base + localPath(presign.presignedUrl), { method: 'PUT', body: data,
    headers: { Origin: origin, 'Content-Type': 'text/plain' } });
  assert.ok(upload.ok, await upload.text());
  const confirmed = await json(await request('/api/v1/panel/media/confirm', { method: 'POST', body: { key: presign.key } }));
  const downloaded = await fetch(base + localPath(confirmed.url));
  assert.equal(await downloaded.text(), data.toString());
  assert.equal(downloaded.headers.get('x-content-type-options'), 'nosniff');
  const invalid = await fetch(base + localPath(confirmed.url) + 'tampered');
  assert.equal(invalid.status, 403);
  await json(await request('/api/v1/panel/media/presign', { method: 'POST', body: {
    uploadType: 'article', fileName: 'bad.exe', contentType: 'application/octet-stream', fileSize: '1',
  } }), 400);
  console.log('PASS: local upload, confirmation, authenticated metadata, signed download and invalid links');
  const publicTicket = await request('/api/v1/public/tickets', { method: 'POST', authenticated: false, body: {
    type: 'support', subject: 'Support without an email', description: 'Email delivery is not configured.', creatorName: 'TestPlayer',
  } });
  await json(publicTicket, 201);
  assert.ok(publicTicket.headers.getSetCookie().some(value => value.startsWith('ticket_auth_')));
  console.log('PASS: public support ticket creation without an email address');

  const ticket = await json(await request('/api/v1/panel/tickets', { method: 'POST', body: {
    type: 'support', subject: 'Access link verification', description: 'Private ticket fixture', creatorName: 'Test player',
  } }), 201);
  const link = await json(await request('/api/v1/panel/tickets/' + ticket.id + '/access-link'));
  const token = new URL(link.url).searchParams.get('token');
  assert.ok(token);
  assert.equal((await request('/api/v1/public/tickets/' + ticket.id, { authenticated: false })).status, 403);
  assert.equal((await request('/api/v1/public/tickets/' + ticket.id + '?token=' + token, { authenticated: false })).status, 200);
  console.log('PASS: private ticket access and staff sharing link');

  const roles = await json(await request('/api/v1/panel/roles'));
  const role = roles.roles.find(r => r.name.toLowerCase() === 'moderator') || roles.roles.find(r => r.name !== 'Super Admin');
  assert.ok(role);
  const staffEmail = 'staff-' + randomBytes(5).toString('hex');
  await json(await request('/api/v1/panel/staff/invite', { method: 'POST', body: { username: staffEmail + '@example.test', role: role.name } }), 400);
  await json(await request('/api/v1/panel/staff/invite', { method: 'POST', body: { username, role: role.name } }), 409);
  await json(await request('/api/v1/panel/staff/invite', { method: 'POST', body: { username: staffEmail, role: role.name } }), 201);
  const staffList = await json(await request('/api/v1/panel/staff'));
  const pending = staffList.staff.find(member => member.email === staffEmail);
  assert.ok(pending);
  const invitation = await json(await request('/api/v1/panel/staff/invitations/' + pending.id + '/link'));
  const inviteToken = new URL(invitation.url).searchParams.get('token');
  await json(await request('/api/v1/public/staff/invitations/accept', { method: 'POST', authenticated: false, body: { token: inviteToken, password } }));
  const ownerCookies = cookies;
  const staffLogin = await request('/api/v1/panel/auth/login', { method: 'POST', authenticated: false, body: { identifier: staffEmail, password } });
  await json(staffLogin); saveSession(staffLogin);
  const staffMe = await json(await request('/api/v1/panel/auth/me'));
  assert.ok(JSON.stringify(staffMe).includes(staffEmail));
  assert.equal(staffMe.username, staffEmail);
  assert.notEqual(staffMe.role, 'Super Admin', 'Staff login must not become the owner');
  await json(await request('/api/v1/panel/auth/profile', { method: 'PATCH', body: { username } }), 409);
  await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  cookies = ownerCookies;
  console.log('PASS: password-based staff invitation and independent staff session');

  const oldCookie = cookies;
  await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  cookies = oldCookie;
  assert.equal((await request('/api/v1/panel/auth/me')).status, 401, 'Logout must invalidate the stored session');
  for (const identifier of [username, username.toUpperCase()]) {
    const login = await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier, password } });
    await json(login); saveSession(login);
    assert.equal((await request('/api/v1/panel/auth/me')).status, 200);
    await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  }
  const login = await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: username, password } });
  await json(login); saveSession(login);
  const renamed = 'renamedowner';
  await json(await request('/api/v1/panel/auth/profile', { method: 'PATCH', body: { username: renamed } }));
  assert.equal((await json(await request('/api/v1/panel/auth/me'))).username, renamed);
  await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  await json(await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: username, password } }), 401);
  const renamedLogin = await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: renamed.toUpperCase(), password } });
  await json(renamedLogin); saveSession(renamedLogin);
  await json(await request('/api/v1/panel/auth/profile', { method: 'PATCH', body: { username } }));
  await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  console.log('PASS: username-only login, email rejection, case-insensitivity, owner rename persistence and logout revocation');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
