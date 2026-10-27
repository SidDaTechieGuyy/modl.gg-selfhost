/* global fetch */
import assert from 'node:assert/strict';
import process from 'node:process';
import console from 'node:console';
import { Buffer } from 'node:buffer';
import { URL } from 'node:url';
const base = process.env.E2E_BASE_URL || 'http://localhost:8091';
const origin = process.env.E2E_ORIGIN || 'http://localhost:8091';
let cookie = '';
const version = value => Number((value._meta ?? value.meta)?.version ?? 0);
async function api(path, method = 'GET', body, status = 200) {
 const response = await fetch(base + '/api' + path, { method, headers: {
  Origin: origin, Accept: 'application/json', Cookie: cookie,
  ...(body === undefined ? {} : {'Content-Type':'application/json'}) },
  body: body === undefined ? undefined : JSON.stringify(body) });
 const text = await response.text();
 assert.equal(response.status,status,`${path}: ${response.status}: ${text}`);
 return { data: JSON.parse(text), response };
}
async function upload(name, type, contents) {
 const bytes = Buffer.from(contents);
 const signed = (await api('/v1/panel/media/presign','POST',{fileName:name,uploadType:type,contentType:'text/plain',fileSize:String(bytes.length)})).data;
 const url = new URL(signed.presignedUrl);
 const put = await fetch(base + url.pathname + url.search,{method:'PUT',headers:signed.requiredHeaders,body:bytes});
 assert.equal(put.status,204);
 return (await api('/v1/panel/media/confirm','POST',{key:signed.key})).data;
}
async function main() {
 assert.equal(process.env.E2E_DISPOSABLE,'1','Use a disposable instance only');
 const open = (await api('/v1/public/registration/status')).data.registrationOpen;
 const auth = open ? await api('/v1/public/registration','POST',{username:'testowner',password:'Modl-test-password-2026!',serverName:'Usage verification'},201)
  : await api('/v1/panel/auth/login','POST',{identifier:'testowner',password:'Modl-test-password-2026!'});
 cookie = auth.response.headers.getSetCookie().find(v=>v.startsWith('MODL_SESSION=')).split(';')[0];
 if(process.env.E2E_RESET === '1') {
  const previous=(await api('/v1/panel/storage/files')).data.files;
  if(previous.length)await api('/v1/panel/storage/bulk-delete','POST',{keys:previous.map(file=>file.key)});
 }
 assert.equal((await api('/v1/panel/storage/files')).data.files.length,0,'Requires empty disposable storage');
 const objects = await Promise.all([upload('zeta.txt','article','zeta'),upload('alpha.txt','ticket','alpha'),upload('beta.txt','evidence','beta!!')]);
 const files = (await api('/v1/panel/storage/files')).data.files;
 assert.equal(files.length,3);
 assert.equal(Number((await api('/v1/panel/storage/quota')).data.usedBytes ?? 0),15);
 for(const object of objects) {
  const url = new URL(object.url); const response = await fetch(base+url.pathname+url.search);
  assert.equal(response.status,200); assert.equal((await response.arrayBuffer()).byteLength,Number(object.size));
 }
 const first = (await api('/v1/panel/settings/replay-retention')).data;
 const saved = (await api('/v1/panel/settings/replay-retention','PATCH',{expectedVersion:version(first),enabled:true,days:17})).data;
 assert.equal((await api('/v1/panel/settings/replay-retention')).data.data.days,17);
 await api('/v1/panel/settings/replay-retention','PATCH',{expectedVersion:version(saved),days:0},400);
 await api('/v1/panel/settings/replay-retention','PATCH',{expectedVersion:version(first),days:33},409);
 const disabled=(await api('/v1/panel/settings/replay-retention','PATCH',{expectedVersion:version(saved),enabled:false})).data;
 assert.equal(disabled.data.enabled,false);
 await api('/v1/panel/settings/replay-retention','PATCH',{expectedVersion:version(disabled),enabled:first.data.enabled,days:first.data.days});
 await api('/v1/panel/storage/bulk-delete','POST',{keys:['different-instance/article/file.txt']},403);
 assert.equal((await api('/v1/panel/storage/files')).data.files.length,3);
 assert.equal((await api('/v1/panel/storage/bulk-delete','POST',{keys:[objects[0].key]})).data.deleted,1);
 const oldUrl=new URL(objects[0].url);assert.equal((await fetch(base+oldUrl.pathname+oldUrl.search)).status,404);
 assert.equal((await api('/v1/panel/storage/files')).data.files.length,2);
 assert.equal(Number((await api('/v1/panel/storage/quota')).data.usedBytes ?? 0),11);
 assert.equal((await api('/v1/panel/storage/bulk-delete','POST',{keys:objects.slice(1).map(o=>o.key)})).data.deleted,2);
 assert.equal((await api('/v1/panel/storage/files')).data.files.length,0);
 assert.equal(Number((await api('/v1/panel/storage/quota')).data.usedBytes ?? 0),0);
 console.log('PASS: real storage totals, listing, downloads, single/bulk deletion, denied foreign keys, persisted retention save/toggle, invalid values and version conflict');
 if(process.env.E2E_KEEP_FILES==='1') {
  for(let i=0;i<12;i++)await upload(`fixture-${String(i).padStart(2,'0')}.txt`,i%3===0?'ticket':'article','x'.repeat(i+1));
  console.log('Seeded 12 disposable files for UI search, filtering, sorting and pagination checks');
 }
}
main().catch(error=>{console.error(error);process.exitCode=1;});
