import assert from 'node:assert/strict';
import { test } from 'node:test';
import { registrationSchema } from './registration-schema.ts';

const values = { serverName: 'Test Server', username: 'owner', password: '12345678', confirmPassword: '12345678' };
test('registration accepts an eight-character password with matching confirmation', () => {
  assert.equal(registrationSchema.safeParse(values).success, true);
  assert.equal('email' in registrationSchema.parse(values), false);
});
test('registration rejects short passwords and missing or mismatched confirmation', () => {
  assert.equal(registrationSchema.safeParse({ ...values, password: '1234567', confirmPassword: '1234567' }).success, false);
  for (const confirmation of ['', 'different']) {
    const parsed = registrationSchema.safeParse({ ...values, confirmPassword: confirmation });
    assert.equal(parsed.success, false);
    if (!parsed.success) assert.ok(parsed.error.issues.some(issue => issue.path[0] === 'confirmPassword'));
  }
});
test('registration retains the bcrypt UTF-8 byte limit', () => {
  const password = '界'.repeat(25);
  assert.equal(registrationSchema.safeParse({ ...values, password, confirmPassword: password }).success, false);
});
test('account usernames cannot be email addresses', () => {
  assert.equal(registrationSchema.safeParse({ ...values, username: 'owner@example.test' }).success, false);
});
