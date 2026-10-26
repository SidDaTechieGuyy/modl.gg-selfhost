import test from 'node:test';
import assert from 'node:assert/strict';
import { accessibleSubCategories, resolveSubCategoryId, type SettingsSubCategoryAccess } from './settings-access.ts';
const access = (owner: boolean, ...permissions: string[]): SettingsSubCategoryAccess => ({
  isSuperAdmin: owner, hasPermission: id => permissions.includes(id),
});
test('owner can configure the instance and its integrations', () => {
  assert.deepEqual(accessibleSubCategories('general', access(true, 'admin.settings.view', 'admin.settings.view.storage')).map(s => s.id),
    ['usage', 'server-config', 'webhooks', 'migration']);
});
test('AI settings require the same administrative permission as other ticket settings', () => {
  assert.ok(accessibleSubCategories('tickets', access(false, 'admin.settings.view')).some(s => s.id === 'ai-moderation'));
  assert.deepEqual(accessibleSubCategories('tickets', access(false)), []);
});
test('unknown or inaccessible selections resolve to an accessible section', () => {
  assert.equal(resolveSubCategoryId('general', 'server-config', access(false, 'admin.settings.view')), 'webhooks');
  assert.equal(resolveSubCategoryId('general', 'missing', access(false)), null);
});
