import { readFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';

const accessFile = process.env.QA_PREVIEW_ACCESS_FILE;
const accounts = new Map<string, string>();
if (accessFile) {
  for (const line of readFileSync(accessFile, 'utf8').split(/\r?\n/)) {
    const separator = line.indexOf(': ');
    if (separator > 0) accounts.set(line.slice(0, separator), line.slice(separator + 2));
  }
}

test.skip(!accessFile, 'Requires the private preview credentials file; credentials are never embedded in tests.');
test.use({ trace: 'off', screenshot: 'off' });

const login = async (page: Page, username: string) => {
  const password = accounts.get(username);
  if (!password) throw new Error(`Missing preview account ${username}.`);
  await page.goto('/login');
  await page.getByPlaceholder('Ví dụ: admin hoặc manager_demo').fill(username);
  await page.getByPlaceholder('Nhập mật khẩu').fill(password);
  await page.getByRole('button', { name: /Đăng nhập$/ }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('.vroad-brand').first()).toBeVisible();
};

for (const username of ['admin', 'editor_demo', 'manager_demo', 'viewer_demo']) {
  test(`HTTPS browser login and role enforcement: ${username}`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.name));
    await login(page, username);
    const token = await page.evaluate(() => localStorage.getItem('kcht_access_token'));
    const headers = { Authorization: `Bearer ${token}` };
    const identity = await page.request.get('/api/auth/me', { headers });
    expect(identity.status()).toBe(200);
    expect((await identity.json()).username).toBe(username);
    const operations = await page.request.get('/api/admin/operations/summary', { headers });
    expect(operations.status()).toBe(username === 'admin' ? 200 : 403);
    const cookies = await page.context().cookies();
    const refreshCookie = cookies.find(cookie => cookie.name === 'vroad_preview_refresh');
    expect(refreshCookie?.httpOnly).toBe(true);
    expect(refreshCookie?.secure).toBe(true);
    expect(errors).toEqual([]);
    if (username === 'admin' && process.env.QA_PREVIEW_SCREENSHOT) {
      await page.screenshot({ path: process.env.QA_PREVIEW_SCREENSHOT, fullPage: true });
    }
    await page.request.post('/api/auth/logout', { headers, data: {} });
  });
}

test('HTTPS administrator opens the deployed OpenLayers survey map', async ({ page }) => {
  await login(page, 'admin');
  await page.goto('/map');
  await expect(page.locator('.ol-viewport')).toBeVisible();
  await expect(page.locator('.ol-viewport canvas').first()).toBeVisible({ timeout: 20000 });
  const token = await page.evaluate(() => localStorage.getItem('kcht_access_token'));
  const overview = await page.request.get('/api/vroad/dashboard', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(overview.status()).toBe(200);
  expect(await overview.json()).toMatchObject({ assets: 2614, defects: 3404, iriSegments: 221 });
  await page.request.post('/api/auth/logout', {
    headers: { Authorization: `Bearer ${token}` }, data: {},
  });
});
