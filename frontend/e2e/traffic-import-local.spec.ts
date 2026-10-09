import { test, expect } from '@playwright/test';

test.skip(!process.env.DEMO_ADMIN_PASSWORD || !process.env.DEMO_MANAGER_PASSWORD,
  'Local demo accounts are available only when loaded from ignored .env');

const signIn = async (page: import('@playwright/test').Page,
  request: import('@playwright/test').APIRequestContext, username: string, password: string) => {
  const login = await request.post(`${process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089'}/api/auth/login`, {
    data: { username, password },
  });
  expect(login.status()).toBe(200);
  const { accessToken } = await login.json();
  await page.addInitScript((token: string) => localStorage.setItem('kcht_access_token', token), accessToken);
};

test('admin uploads JSON, rejects template placeholders and opens traffic charts', async ({ page, request }) => {
  await signIn(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  await page.goto('/admin/traffic-import');
  await expect(page.getByRole('heading', { name: 'Nhập quan sát giao thông' })).toBeVisible();

  const payload = { requestKey: 'qa-e2e-traffic-001', sourceSystem: 'LOCAL_UPLOAD',
    station: { code: 'EXAMPLE-TRAFFIC-01', name: 'Điểm minh họa', routeName: 'Tuyến minh họa',
      branchId: 'kqldb_1', segmentLengthKm: 2.5 },
    counts: [{ eventKey: 'qa-e2e-count-001', direction: 'Bắc', lane: 1,
      windowStart: '2026-10-07T08:00:00+07:00', windowEnd: '2026-10-07T08:15:00+07:00',
      vehicleClass: 'Ô tô con', vehicleCount: 16 }], snapshots: [] };
  await page.getByLabel('Chọn tệp JSON').setInputFiles({ name: 'traffic.json',
    mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(payload), 'utf8') });
  await expect(page.getByLabel('Nội dung giao thông JSON')).toContainText('EXAMPLE-TRAFFIC-01');
  await page.getByRole('button', { name: 'Nhập vào bản demo' }).click();
  await expect(page.getByText(/thay các mã minh họa/)).toBeVisible();

  payload.station.code = 'QA-TRAFFIC-LOCAL-01';
  await page.route('**/api/vroad/traffic/imports', async (route) => {
    expect(route.request().postDataJSON().station.code).toBe(payload.station.code);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      batchId: 8, requestKey: payload.requestKey, stationCode: payload.station.code,
      importedCounts: 1, importedSnapshots: 0,
    }) });
  });
  await page.getByLabel('Nội dung giao thông JSON').fill(JSON.stringify(payload));
  await page.getByRole('button', { name: 'Nhập vào bản demo' }).click();
  await expect(page.getByText('Đã nhập 1 phép đếm và 0 lát cắt')).toBeVisible();
  await page.getByRole('button', { name: 'Xem biểu đồ' }).click();
  await expect(page.getByRole('heading', { name: 'Lưu lượng phương tiện' })).toBeVisible();
});

test('regional manager cannot open the traffic import page', async ({ page, request }) => {
  await signIn(page, request, 'manager_demo', process.env.DEMO_MANAGER_PASSWORD!);
  await page.goto('/admin/traffic-import');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Nhập quan sát giao thông' })).toHaveCount(0);
});
