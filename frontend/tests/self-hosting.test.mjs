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
import { create, fromBinary, toBinary } from '@bufbuild/protobuf';
import { ClientHelloSchema, ClientKind, RealtimeEnvelopeSchema, Topic } from '@modl-gg/proto/modl/v1/realtime_pb.ts';

const base = process.env.E2E_BASE_URL || 'http://localhost:8080';
const origin = process.env.E2E_ORIGIN || 'http://localhost:8080';
const username = 'testowner';
const email = 'owner@example.test';
const password = 'Modl-test-password-2026!';
let cookies = '';
const unwrap = value => value;
async function request(path, { method = 'GET', body, authenticated = true, headers = {} } = {}) {
  const response = await fetch(base + path, {
    method, headers: { Origin: origin, Accept: 'application/json',
      ...(authenticated && cookies ? { Cookie: cookies } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return response;
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

async function websocket(allowedOrigin, expectedStatus) {
  return new Promise((resolve, reject) => {
    const url = new URL('/api/v1/realtime/ws', base);
    const timeout = setTimeout(() => { req.destroy(); reject(new Error('WebSocket handshake timed out')); }, 15000);
    const finish = error => { clearTimeout(timeout); if (error) reject(error); else resolve(); };
    const req = http.request(url, { headers: { Connection: 'Upgrade', Upgrade: 'websocket',
      'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': randomBytes(16).toString('base64'),
      Origin: allowedOrigin, Cookie: cookies } });
    req.on('error', finish);
    req.on('response', response => {
      response.resume();
      try { assert.equal(response.statusCode, expectedStatus); finish(); } catch (e) { finish(e); }
    });
    req.on('upgrade', (response, socket, head) => {
      if (expectedStatus !== 101) { socket.destroy(); finish(new Error('Untrusted origin upgraded')); return; }
      const hello = toBinary(RealtimeEnvelopeSchema, create(RealtimeEnvelopeSchema, {
        protocolVersion: 1, payload: { case: 'clientHello', value: create(ClientHelloSchema, {
          clientKind: ClientKind.PANEL, protocolVersion: 1, supportedTopics: [Topic.PANEL_DASHBOARD],
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
    const payload = { username, email, password, serverName: 'Verification server' };
    const responses = await Promise.all([request('/api/v1/public/registration', { method: 'POST', body: payload, authenticated: false }),
      request('/api/v1/public/registration', { method: 'POST', body: payload, authenticated: false })]);
    assert.deepEqual(responses.map(r => r.status).sort(), [201, 409], 'Exactly one racing registration must succeed');
    saveSession(responses.find(r => r.status === 201));
  } else {
    const login = await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: username, password } });
    await json(login); saveSession(login);
  }
  assert.equal((await json(await request('/api/v1/public/registration/status'))).registrationOpen, false);
  await json(await request('/api/v1/public/registration', { method: 'POST', body: { username, email, password, serverName: 'Other' } }), 409);
  await json(await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier: username, password: 'incorrect-password' } }), 401);
  const me = await json(await request('/api/v1/panel/auth/me'));
  assert.ok(JSON.stringify(me).includes(email));
  const preflight = await request('/api/v1/panel/auth/me', { method: 'OPTIONS', headers: { 'Access-Control-Request-Method': 'GET' } });
  assert.ok(preflight.ok);
  assert.equal(preflight.headers.get('access-control-allow-origin'), origin);
  assert.equal(preflight.headers.get('access-control-allow-credentials'), 'true');
  const untrusted = 'https://untrusted.invalid';
  const denied = await request('/api/v1/panel/auth/me', { method: 'OPTIONS', headers: { Origin: untrusted, 'Access-Control-Request-Method': 'GET' } });
  const permissive = process.env.E2E_PERMISSIVE_ORIGINS === '1';
  assert.equal(denied.status, permissive ? 200 : 403);
  if (permissive) assert.equal(denied.headers.get('access-control-allow-origin'), untrusted);
  await websocket(origin, 101);
  await websocket(untrusted, permissive ? 101 : 403);
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
  const staffEmail = 'staff-' + randomBytes(5).toString('hex') + '@example.test';
  await json(await request('/api/v1/panel/staff/invite', { method: 'POST', body: { email: staffEmail, role: role.name } }), 201);
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
  assert.ok(!JSON.stringify(staffMe).includes(email), 'Staff login must not become the owner');
  await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  cookies = ownerCookies;
  console.log('PASS: password-based staff invitation and independent staff session');

  const oldCookie = cookies;
  await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  cookies = oldCookie;
  assert.equal((await request('/api/v1/panel/auth/me')).status, 401, 'Logout must invalidate the stored session');
  for (const identifier of [username, email]) {
    const login = await request('/api/v1/panel/auth/login', { method: 'POST', body: { identifier, password } });
    await json(login); saveSession(login);
    assert.equal((await request('/api/v1/panel/auth/me')).status, 200);
    await json(await request('/api/v1/panel/auth/logout', { method: 'POST' }));
  }
  console.log('PASS: logout revocation and login by both username and email');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
