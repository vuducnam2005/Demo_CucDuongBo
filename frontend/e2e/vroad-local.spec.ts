import { test, expect } from '@playwright/test';

test.use({
  launchOptions: process.env.QA_CHROME_PATH ? { executablePath: process.env.QA_CHROME_PATH } : {},
});

test.skip(!process.env.DEMO_ADMIN_PASSWORD || !process.env.DEMO_MANAGER_PASSWORD,
  'Local demo credentials must be supplied from ignored .env; CI does not use live accounts.');

const loginLocally = async (page: import('@playwright/test').Page,
  request: import('@playwright/test').APIRequestContext,
  username: string, password: string) => {
  const login = await request.post(`${process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089'}/api/auth/login`, {
    data: { username, password },
  });
  expect(login.status()).toBe(200);
  const { accessToken } = await login.json();
  await page.addInitScript((token: string) => {
    localStorage.setItem('kcht_access_token', token);
  }, accessToken);
  return accessToken as string;
};

test('central map opens the actual workbook image and processed case', async ({ page, request }) => {
  const token = await loginLocally(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  const mapPoints = page.waitForResponse((response) => response.url().includes('/api/vroad/map/points') && response.ok());
  const mapAssets = page.waitForResponse((response) => response.url().includes('/api/vroad/map/assets') && response.ok());
  await page.goto('/map');
  const { content } = await (await mapPoints).json();
  const { content: assetMarkers } = await (await mapAssets).json();
  await expect(page.getByRole('heading', { name: 'Bản đồ hư hỏng VroadAI' })).toBeVisible();
  expect(content.length).toBeGreaterThan(0);
  expect(assetMarkers.length).toBeGreaterThan(0);
  await expect(page.getByText(`${content.length} hư hỏng`)).toBeVisible();
  await expect(page.getByText('Danh mục toàn tuyến')).toHaveCount(0);
  await page.getByPlaceholder('Tìm mã điểm hoặc tài sản trong khung bản đồ').fill(content[0].recordKey);
  await page.getByRole('button', { name: 'Xem điểm' }).click();
  await expect(page.getByText(/Tuyến:/)).toBeVisible();
  await expect(page.getByText('Danh mục toàn tuyến')).toBeVisible();
  await expect(page.getByText('QL.1', { exact: true })).toBeVisible();
  await expect(page.getByText('Hư hỏng cùng tuyến')).toHaveCount(0);
  const photo = page.frameLocator('iframe[title^="Ảnh khảo sát"]').first().locator('img');
  await expect.poll(async () => photo.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(100);
  await page.getByRole('button', { name: /^Phóng to ảnh hư hỏng/ }).click();
  await expect(page.getByRole('dialog', { name: /Ảnh hư hỏng/ })).toBeVisible();
  await expect.poll(async () => page.frameLocator('iframe[title^="Ảnh phóng to"]').locator('img')
    .evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(100);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: /Ảnh hư hỏng/ })).not.toBeVisible();
  const map = page.getByTestId('vroad-map');
  const bounds = await map.boundingBox();
  expect(bounds).not.toBeNull();
  await map.click({ position: { x: Math.round(bounds!.width - 30), y: 90 } });
  await expect(page.getByText('Chọn một điểm đánh dấu trên bản đồ để xem đúng đoạn khảo sát và ảnh hư hỏng.')).toBeVisible();
  await expect(page.getByText('Danh mục toàn tuyến')).toHaveCount(0);
  await page.getByPlaceholder('Tìm mã điểm hoặc tài sản trong khung bản đồ').fill(assetMarkers[0].recordKey);
  await page.getByRole('button', { name: 'Xem điểm' }).click();
  await expect(page.getByText(`Mã: ${assetMarkers[0].recordKey}`)).toBeVisible();
  const selectedAsset = await request.get(
    `${process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089'}/api/vroad/assets/${assetMarkers[0].recordId}/map`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(selectedAsset.status()).toBe(200);
  const assetDetail: { routeSide: string } = await selectedAsset.json();
  expect(assetDetail.routeSide).toBeTruthy();
  await expect(page.getByText(`Chiều tuyến: ${assetDetail.routeSide}`, { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: `Phóng to ảnh tài sản ${assetMarkers[0].recordKey}` })).toBeVisible();
  await expect(page.getByText('Chưa có hồ sơ xử lý')).toHaveCount(0);
  const caseResponse = page.waitForResponse((response) => response.url().includes('/api/vroad/cases') && response.ok());
  await page.goto('/cases');
  const cases = await (await caseResponse).json();
  const withEvidence = cases.content.find((item: { hasEvidence: boolean }) => item.hasEvidence);
  expect(withEvidence).toBeTruthy();
  await expect(page.getByRole('heading', { name: 'Hồ sơ hư hỏng đã xử lý' })).toBeVisible();
  await page.getByRole('row').filter({ hasText: withEvidence.recordKey }).click();
  const detail = page.getByRole('dialog', { name: 'Hồ sơ đã xử lý xong' });
  await expect(detail.getByText('Nội dung xử lý')).toBeVisible();
  await expect(detail.getByText(/Đã xử lý xong bởi:/)).toBeVisible();
  const evidence = detail.getByAltText('Ảnh minh chứng sau xử lý');
  await expect(evidence).toBeVisible();
  await expect.poll(async () => evidence.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0);
});

test('asset list opens only the selected map asset and enlarges its source photo', async ({ page, request }) => {
  const token = await loginLocally(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  const backend = process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089';
  const response = await request.get(`${backend}/api/datasets/vroad_assets/records?page=0&size=1&sort=id,asc`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  const records = await response.json();
  const record = records.content[0];
  expect(record).toBeTruthy();
  const assetResponse = await request.get(`${backend}/api/vroad/assets/${record.id}/map`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(assetResponse.ok()).toBeTruthy();
  const initialAsset = await assetResponse.json();
  expect(initialAsset.relatedDefects).toEqual([]);
  const relatedResponse = await request.get(`${backend}/api/vroad/assets/${record.id}/map?includeRelated=true`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(relatedResponse.ok()).toBeTruthy();
  const asset = await relatedResponse.json();
  expect(asset.sourceImageUrl).toMatch(/^https:\/\/platform\.vroad\.vn\//);
  expect(asset.roadCatalog?.name).toBe('QL.1');
  expect(asset.relatedDefects.length).toBeGreaterThan(0);
  expect(asset.relatedDefects[0].routeName).toBe(asset.routeName);
  expect(asset.relatedDefects[0].chainage).toBe(asset.chainage);
  expect(asset.relatedDefects[0].routeSide).toBe(asset.routeSide);
  expect(asset.relatedDefects[0].defectSide).toBe(asset.assetSide);
  expect(asset.relatedDefectsTruncated).toBe(false);

  let relatedRequests = 0;
  page.on('request', (pending) => {
    if (pending.url().includes(`/api/vroad/assets/${asset.recordId}/map?includeRelated=true`)) relatedRequests++;
  });
  await page.goto('/assets?datasetKey=vroad_assets');
  const row = page.getByRole('row').filter({ hasText: asset.recordKey }).first();
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Mở GIS' }).click();
  await expect(page).toHaveURL(new RegExp(`assetRecordId=${asset.recordId}`));
  await expect(page.getByText(`Mã: ${asset.recordKey}`)).toBeVisible();
  await expect(page.getByText(`Tuyến: ${asset.routeName}`)).toBeVisible();
  await expect(page.getByText('Danh mục toàn tuyến')).toBeVisible();
  await expect(page.getByText('Hư hỏng ghi nhận gần tài sản')).toHaveCount(0);
  await expect(page.getByText(`Mã hư hỏng: ${asset.relatedDefects[0].recordKey}`, { exact: false })).toHaveCount(0);
  expect(relatedRequests).toBe(0);
  await page.getByRole('button', { name: 'Xem hư hỏng gần tài sản' }).click();
  await expect(page.getByText(`Mã hư hỏng: ${asset.relatedDefects[0].recordKey}`, { exact: false })).toBeVisible();
  expect(relatedRequests).toBe(1);
  await page.locator(`iframe[title="Ảnh khảo sát ${asset.relatedDefects[0].recordKey}"]`).scrollIntoViewIfNeeded();
  const defectPhoto = page.frameLocator(`iframe[title="Ảnh khảo sát ${asset.relatedDefects[0].recordKey}"]`).locator('img');
  await expect.poll(async () => defectPhoto.evaluate((image: HTMLImageElement) => image.naturalWidth),
    { timeout: 15000 }).toBeGreaterThan(100);
  await page.getByRole('button', { name: `Phóng to ảnh hư hỏng ${asset.relatedDefects[0].recordKey}` }).click();
  const enlargedDefectPhoto = page.getByRole('dialog', { name: `Ảnh hư hỏng ${asset.relatedDefects[0].recordKey}` });
  await expect(enlargedDefectPhoto).toBeVisible();
  await enlargedDefectPhoto.getByRole('button', { name: 'Close' }).click();
  await page.getByRole('button', { name: 'Thu gọn' }).click();
  await expect(page.getByText(`Mã hư hỏng: ${asset.relatedDefects[0].recordKey}`, { exact: false })).toHaveCount(0);
  const photo = page.frameLocator(`iframe[title="Ảnh tài sản ${asset.recordKey}"]`).locator('img');
  await expect.poll(async () => photo.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(100);
  await page.getByRole('button', { name: `Phóng to ảnh tài sản ${asset.recordKey}` }).click();
  const enlargedPhoto = page.getByRole('dialog', { name: `Ảnh tài sản ${asset.recordKey}` });
  await expect(enlargedPhoto).toBeVisible();
  await enlargedPhoto.getByRole('button', { name: 'Close' }).click();
  await expect(enlargedPhoto).not.toBeVisible();
  const map = page.getByTestId('vroad-map');
  const bounds = await map.boundingBox();
  expect(bounds).not.toBeNull();
  await map.click({ position: { x: Math.round(bounds!.width - 30), y: 90 } });
  await expect(page.getByText(`Mã: ${asset.recordKey}`)).toHaveCount(0);
  await expect(page.getByText(`Mã hư hỏng: ${asset.relatedDefects[0].recordKey}`, { exact: false })).toHaveCount(0);
  await page.getByText('Hư hỏng', { exact: true }).click();
  await page.getByText('Tài sản', { exact: true }).click();
  await map.click({ position: { x: Math.round(bounds!.width / 2), y: Math.round(bounds!.height / 2) } });
  await expect(page.getByText(`Mã: ${asset.recordKey}`)).toBeVisible();
  await page.getByRole('button', { name: 'Tra cứu hồ sơ tài sản' }).click();
  await expect(page).toHaveURL(new RegExp(`/documents\\?asset=${asset.recordKey}`));
  await expect(page.getByPlaceholder('Tìm kiếm tài liệu theo tên, công trình, người nạp...'))
    .toHaveValue(asset.recordKey);
  await page.getByRole('button', { name: 'Tải lên tài liệu' }).click();
  await expect(page.getByPlaceholder('VD: bridge_01, QL.1-Km120...')).toHaveValue(asset.recordKey);
});

test('branch cannot see national survey data or access the old national map', async ({ page, request }) => {
  const token = await loginLocally(page, request, 'manager_demo', process.env.DEMO_MANAGER_PASSWORD!);
  await page.goto('/map');
  await expect(page.getByRole('heading', { name: 'Bản đồ hư hỏng VroadAI' })).toBeVisible();
  await expect(page.getByText('0 hư hỏng')).toBeVisible();
  await expect(page.getByText('0 tài sản')).toBeVisible();
  const national = await page.request.get(`${process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089'}/api/dashboard/summary`, {
    headers: { Authorization: `Bearer ${await page.evaluate(() => localStorage.getItem('kcht_access_token'))}` },
  });
  expect(national.status()).toBe(403);
  const outsideAsset = await request.get(`${process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089'}/api/vroad/assets/1/map`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(outsideAsset.status()).toBe(404);
  await page.goto('/map/assets');
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('central administrator submits a note and image without modifying the demo database', async ({ page, request }) => {
  await loginLocally(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  const mapPoints = page.waitForResponse((response) => response.url().includes('/api/vroad/map/points') && response.ok());
  await page.goto('/map');
  const { content } = await (await mapPoints).json();
  const unresolved = content.find((point: { resolvedAt: string | null }) => !point.resolvedAt);
  expect(unresolved).toBeTruthy();
  await page.getByPlaceholder('Tìm mã điểm hoặc tài sản trong khung bản đồ').fill(unresolved.recordKey);
  await page.getByRole('button', { name: 'Xem điểm' }).click();
  await page.getByRole('button', { name: 'Đã xử lý xong' }).first().click();
  let submitted: Buffer | null = null;
  await page.route('**/api/vroad/defects/*/resolve', async (route) => {
    submitted = route.request().postDataBuffer();
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
  await page.getByRole('textbox', { name: 'Nội dung xử lý' }).fill('Xử lý hư hỏng trong kịch bản kiểm thử giao diện demo.');
  await page.getByLabel('Ảnh minh chứng').setInputFiles({
    name: 'qa.png', mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/uXcAAAAASUVORK5CYII=', 'base64'),
  });
  await page.getByRole('button', { name: 'Lưu vào hồ sơ' }).click();
  await expect(page.getByRole('dialog', { name: 'Xác nhận đã xử lý hư hỏng' })).not.toBeVisible();
  expect(submitted).not.toBeNull();
  expect(Buffer.from(submitted ?? []).toString('utf8')).toContain('Xử lý hư hỏng trong kịch bản kiểm thử giao diện demo.');
  expect(Buffer.from(submitted ?? []).toString('utf8')).toContain('qa.png');
  expect(Buffer.from(submitted ?? []).toString('utf8')).toContain('Content-Type: image/png');
});

test('central dashboard and traffic display live local aggregates', async ({ page, request }) => {
  await loginLocally(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  const overview = page.waitForResponse((response) => response.url().includes('/api/vroad/dashboard') && response.ok());
  await page.goto('/dashboard');
  const totals = await (await overview).json();
  expect(totals.defects).toBeGreaterThan(0);
  await page.goto('/traffic');
  await expect(page.getByRole('heading', { name: 'Lưu lượng phương tiện' })).toBeVisible();
  await expect(page.getByText('Phân loại phương tiện')).toBeVisible();
  await expect(page.getByText('Xe máy')).toBeVisible();
});

test('operations dashboard reconciles live counts and is restricted to administrators', async ({ page, request }) => {
  const adminToken = await loginLocally(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  const endpoint = `${process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089'}/api/admin/operations/summary`;
  const backend = await request.get(endpoint, { headers: { Authorization: `Bearer ${adminToken}` } });
  expect(backend.status()).toBe(200);
  const summary = await backend.json();
  expect(summary.rawRecords).toBeGreaterThan(0);
  expect(summary.rawRecords).toBe(summary.datasets.reduce(
    (sum: number, dataset: { count: number }) => sum + dataset.count, 0
  ));
  expect(summary.auditActivity).toHaveLength(7);
  await page.goto('/admin/operations');
  await expect(page.getByRole('heading', { name: 'Vận hành hệ thống' })).toBeVisible();
  const rawRecordsMetric = page.locator('.ant-statistic').filter({ hasText: 'Bản ghi' })
    .locator('.ant-statistic-content-value');
  await expect(rawRecordsMetric).toBeVisible();
  expect(Number((await rawRecordsMetric.innerText()).replace(/[^0-9]/g, ''))).toBe(summary.rawRecords);
  await expect(page.getByText('Lô tiếp nhận chờ đối soát')).toBeVisible();
  const manager = await request.post(
    `${process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089'}/api/auth/login`,
    { data: { username: 'manager_demo', password: process.env.DEMO_MANAGER_PASSWORD } }
  );
  expect(manager.status()).toBe(200);
  const managerToken = (await manager.json()).accessToken;
  const denied = await request.get(endpoint, { headers: { Authorization: `Bearer ${managerToken}` } });
  expect(denied.status()).toBe(403);
});

test('branch sees only scoped document archive and no national traffic station', async ({ page, request }) => {
  await loginLocally(page, request, 'manager_demo', process.env.DEMO_MANAGER_PASSWORD!);
  await page.goto('/traffic');
  await expect(page.getByText('Chưa có trạm trong phạm vi đơn vị')).toBeVisible();
  await page.goto('/documents');
  await expect(page.getByRole('heading', { name: 'Quản lý Hồ sơ & Tài liệu Kỹ thuật KCHT' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tạo thư mục' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tải lên tài liệu' })).toBeVisible();
});

test('document history keeps the original file after replacement', async ({ page, request }) => {
  const accessToken = await loginLocally(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  const backend = process.env.QA_BACKEND_URL || 'http://127.0.0.1:8089';
  const authorization = { Authorization: `Bearer ${accessToken}` };
  const fileName = `qa-doc-${Date.now()}.pdf`;
  const original = Buffer.from('%PDF-1.4\n%%EOF\n');
  const changed = Buffer.from('%PDF-1.4\n%second\n%%EOF\n');
  const uploaded = await request.post(`${backend}/api/local-documents/upload`, {
    headers: authorization,
    multipart: { file: { name: fileName, mimeType: 'application/pdf', buffer: original } },
  });
  expect(uploaded.status()).toBe(200);
  const { id } = await uploaded.json();
  try {
    await page.goto('/documents');
    await page.getByPlaceholder('Tìm kiếm tài liệu theo tên, công trình, người nạp...').fill(fileName);
    await page.getByRole('button', { name: `Lịch sử ${fileName}` }).click();
    await expect(page.getByText('v1')).toBeVisible();
    await page.getByLabel('Chọn tệp phiên bản mới').setInputFiles({
      name: fileName, mimeType: 'application/pdf', buffer: changed,
    });
    await page.getByRole('button', { name: 'Lưu phiên bản mới' }).click();
    await expect(page.getByText('v2')).toBeVisible();
    const oldFile = await request.get(`${backend}/api/local-documents/${id}/versions/1/file`, {
      headers: authorization,
    });
    const newFile = await request.get(`${backend}/api/local-documents/${id}/file`, {
      headers: authorization,
    });
    expect(oldFile.status()).toBe(200);
    expect(newFile.status()).toBe(200);
    expect(await oldFile.body()).toEqual(original);
    expect(await newFile.body()).toEqual(changed);
  } finally {
    const cleanup = await request.delete(`${backend}/api/local-documents/${id}`, { headers: authorization });
    expect(cleanup.status()).toBe(204);
  }
});

test('admin can inspect the proposed inbound integration without sending external requests', async ({ page, request }) => {
  await loginLocally(page, request, 'admin', process.env.DEMO_ADMIN_PASSWORD!);
  const batches = page.waitForResponse((response) => response.url().includes('/api/vroad/inbound/batches')
    && response.request().method() === 'GET');
  await page.goto('/admin/vroad-inbound');
  expect((await batches).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Tích hợp VroadAI' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Nội dung lô JSON' })).toBeVisible();
  await expect(page.getByText(/chưa tự tạo tài sản/)).toBeVisible();
});
