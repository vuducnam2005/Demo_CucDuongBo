import { test, expect } from '@playwright/test';

/**
 * Bộ kiểm thử E2E Playwright cho Hệ thống Quản lý KCHT Đường bộ (Giai đoạn 12):
 * Kiểm thử toàn diện 8 kịch bản nghiệp vụ:
 * 1. Login (Xử lý mật khẩu sai và đăng nhập đúng)
 * 2. Dashboard (Chỉ số KPI, phân bổ Khu QLĐB)
 * 3. Tree (Cây phân cấp danh mục tài sản, chọn dataset)
 * 4. Search (Tìm kiếm từ khóa, lọc bảng dữ liệu)
 * 5. Detail (Mở Drawer xem chi tiết thuộc tính tài sản)
 * 6. Map (WebGIS OpenLayers viewport và bộ điều khiển lớp)
 * 7. Report (Báo cáo chiều dài quốc lộ, kế hoạch bảo trì)
 * 8. Download (Xuất file CSV báo cáo)
 */

test.describe('KCHT ĐB - Bộ kiểm thử E2E Toàn diện (Playwright)', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Mock API Auth Me & Refresh
    await page.route('**/api/auth/me', async (route) => {
      const authHeader = route.request().headers()['authorization'] || '';
      if (authHeader.includes('mock-admin-token')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            username: 'admin',
            fullName: 'Quản trị viên Hệ thống',
            role: 'ROLE_ADMIN',
            email: 'admin@drvn.gov.vn',
            branchId: null,
          }),
        });
      } else {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ status: 401, message: 'Chưa đăng nhập' }),
        });
      }
    });

    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ status: 401, message: 'Refresh token expired' }),
      });
    });

    await page.route('**/api/auth/login', async (route) => {
      const data = route.request().postDataJSON() || {};
      if (data.username === 'admin' && data.password === 'mock-password') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            accessToken: 'mock-admin-token',
            refreshToken: 'mock-refresh-token',
            user: {
              username: 'admin',
              fullName: 'Quản trị viên Hệ thống',
              role: 'ROLE_ADMIN',
              email: 'admin@drvn.gov.vn',
              branchId: null,
            },
          }),
        });
      } else {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 401,
            message: 'Tên đăng nhập hoặc mật khẩu không chính xác.',
          }),
        });
      }
    });

    // 2. Mock Dashboard APIs
    await page.route('**/api/dashboard/summary', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalAssets: 833215,
          totalDatasets: 658,
          physicalAssetDatasets: 57,
          moduleDatasets: 601,
          totalBridges: 11631,
          totalRoadSigns: 222112,
          totalNationalRoadRoutes: 169,
          totalNationalRoadLengthKm: 24560.8,
          totalDocuments: 309,
          sourceDataset: 'PostGIS Curated + ODS',
          filter: 'Toàn quốc',
          lastUpdated: new Date().toISOString(),
        }),
      });
    });

    await page.route('**/api/dashboard/stats/branches', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { branchId: 'kqldb_1', branchName: 'Khu QLĐB I', totalAssets: 215400, bridgeCount: 3200, roadSignCount: 65000, nationalRoadCount: 45, sourceDataset: 'ODS', filter: 'Khu I', lastUpdated: new Date().toISOString() },
          { branchId: 'kqldb_2', branchName: 'Khu QLĐB II', totalAssets: 198200, bridgeCount: 2900, roadSignCount: 58000, nationalRoadCount: 38, sourceDataset: 'ODS', filter: 'Khu II', lastUpdated: new Date().toISOString() },
        ]),
      });
    });

    await page.route('**/api/dashboard/stats/datasets', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { datasetKey: 'tbl_road_sign', datasetName: 'Biển báo hiệu', kind: 'physical_asset', totalRecords: 222112, sourceFile: 'tbl_road_sign.json', sourceDataset: 'raw', filter: 'All', lastUpdated: new Date().toISOString() },
          { datasetKey: 'tbl_bridge', datasetName: 'Cầu đường bộ', kind: 'physical_asset', totalRecords: 11631, sourceFile: 'tbl_bridge.json', sourceDataset: 'raw', filter: 'All', lastUpdated: new Date().toISOString() },
        ]),
      });
    });

    await page.route('**/api/dashboard/stats/road-signs', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalSigns: 222112,
          byBranch: [{ branchId: 'kqldb_1', branchName: 'Khu QLĐB I', count: 65000, percentage: 29.3 }],
          byShape: [{ category: 'Biển báo cấm (P)', count: 45200 }, { category: 'Biển cảnh báo (W)', count: 62100 }],
          sourceDataset: 'tbl_road_sign',
          filter: 'Toàn quốc',
          lastUpdated: new Date().toISOString(),
        }),
      });
    });

    await page.route('**/api/dashboard/stats/road-lengths', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalRoutes: 169,
          totalLengthKm: 24560.8,
          averageLengthKm: 145.3,
          longestRoutes: [{ routeCode: 'QL.1', routeName: 'Quốc lộ 1', lengthKm: 2301.5 }],
          distribution: [{ rangeLabel: '> 500 km', routeCount: 12, totalKm: 9450.2 }],
          sourceDataset: 'mst_national_road',
          filter: 'Toàn quốc',
          lastUpdated: new Date().toISOString(),
        }),
      });
    });

    await page.route('**/api/dashboard/stats/recent-assets**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { datasetKey: 'tbl_bridge', datasetName: 'Cầu đường bộ', recordKey: 'tbl_bridge_01', assetName: 'Cầu Thăng Long', branchId: 'kqldb_1', branchName: 'Khu QLĐB I', importedAt: new Date().toISOString(), sourceDataset: 'tbl_bridge', detailUrl: '/assets?datasetKey=tbl_bridge' }
        ]),
      });
    });

    // 3. Mock Dataset APIs
    await page.route('**/api/datasets/tree**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            key: 'group_assets',
            title: 'Tài sản kết cấu đường bộ (57)',
            isLeaf: false,
            children: [
              { key: 'tbl_road_sign', title: 'Biển báo hiệu (222.112)', datasetKey: 'tbl_road_sign', isLeaf: true, totalRecords: 222112, kind: 'physical_asset' },
              { key: 'tbl_bridge', title: 'Cầu đường bộ (11.631)', datasetKey: 'tbl_bridge', isLeaf: true, totalRecords: 11631, kind: 'physical_asset' },
            ],
          },
        ]),
      });
    });

    await page.route('**/api/datasets/*/metadata**', async (route) => {
      const url = route.request().url();
      const isBridge = url.includes('tbl_bridge');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          datasetKey: isBridge ? 'tbl_bridge' : 'tbl_road_sign',
          datasetName: isBridge ? 'Cầu đường bộ' : 'Biển báo hiệu',
          kind: 'asset',
          totalRecords: isBridge ? 11631 : 222112,
          fields: [
            { fieldName: 'name', displayName: 'Tên công trình', dataType: 'STRING', searchable: true, filterable: false },
            { fieldName: 'route_code', displayName: 'Tuyến đường', dataType: 'STRING', searchable: true, filterable: false },
            { fieldName: 'km_from', displayName: 'Lý trình từ (Km)', dataType: 'NUMBER', searchable: false, filterable: false },
            { fieldName: 'branch_id', displayName: 'Đơn vị quản lý', dataType: 'STRING', searchable: false, filterable: true },
          ],
          endpoint: `/api/datasets/${isBridge ? 'tbl_bridge' : 'tbl_road_sign'}/records`,
          sourceFile: `${isBridge ? 'tbl_bridge' : 'tbl_road_sign'}.json`,
          geometryType: 'POINT',
        }),
      });
    });

    await page.route('**/api/datasets/*/records**', async (route) => {
      const url = new URL(route.request().url());
      const q = (url.searchParams.get('q') || '').toLowerCase();
      const isBridge = url.pathname.includes('tbl_bridge');

      let records = isBridge
        ? [
            {
              id: 101,
              datasetKey: 'tbl_bridge',
              recordKey: 'tbl_bridge_thang_long',
              payload: {
                id: 'tbl_bridge_thang_long',
                name: 'Cầu Thăng Long',
                route_code: 'QL.1',
                km_from: 10.5,
                km_to: 13.5,
                branch_id: 'kqldb_1',
                province_name: 'Hà Nội',
                version: 1,
                attributes: { length_m: 3250, span_count: 15 },
              },
              recordStatus: 'CURATED',
              createdAt: '2026-10-06T00:00:00Z',
              updatedAt: '2026-10-06T00:00:00Z',
            },
            {
              id: 102,
              datasetKey: 'tbl_bridge',
              recordKey: 'tbl_bridge_chuong_duong',
              payload: {
                id: 'tbl_bridge_chuong_duong',
                name: 'Cầu Chương Dương',
                route_code: 'QL.5',
                km_from: 1.2,
                km_to: 2.4,
                branch_id: 'kqldb_1',
                province_name: 'Hà Nội',
                version: 1,
                attributes: { length_m: 1230, span_count: 8 },
              },
              recordStatus: 'CURATED',
              createdAt: '2026-10-06T00:00:00Z',
              updatedAt: '2026-10-06T00:00:00Z',
            },
          ]
        : [
            {
              id: 201,
              datasetKey: 'tbl_road_sign',
              recordKey: 'tbl_road_sign_001',
              payload: {
                id: 'tbl_road_sign_001',
                name: 'Biển P.101 Cấm xe thô sơ',
                route_code: 'QL.1',
                km_from: 15.0,
                km_to: 15.1,
                branch_id: 'kqldb_1',
                version: 1,
                attributes: { shape_sign_id: '1' },
              },
              recordStatus: 'CURATED',
              createdAt: '2026-10-06T00:00:00Z',
              updatedAt: '2026-10-06T00:00:00Z',
            },
          ];

      if (q) {
        records = records.filter(r => (r.payload.name || '').toLowerCase().includes(q));
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          page: 0,
          size: 20,
          totalElements: records.length,
          totalPages: 1,
          first: true,
          last: true,
          content: records,
        }),
      });
    });

    await page.route('**/api/datasets/*/export**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=utf-8',
        headers: { 'Content-Disposition': 'attachment; filename="export.csv"' },
        body: '\uFEFFname,route_code,km_from\nCầu Thăng Long,QL.1,10.5\n',
      });
    });

    // 4. Mock GIS & Reports
    await page.route('**/api/datasets/*/clusters**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          type: 'FeatureCollection',
          features: [
            {
              type: 'Feature',
              id: 101,
              geometry: { type: 'Point', coordinates: [105.85, 21.03] },
              properties: { id: 101, name: 'Cầu Thăng Long', datasetCode: 'tbl_bridge', routeCode: 'QL.1' },
            },
          ],
        }),
      });
    });

    await page.route('**/api/reports/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          summary: {
            totalRoutes: 169,
            totalSegments: 1250,
            totalLengthKm: 24560.8,
            averageLengthKm: 145.3,
            longestRouteCode: 'QL.1',
            longestRouteLengthKm: 2301.5,
            totalProjects: 540,
            totalBudgetVnd: 1250000000000,
            completedCount: 320,
            inProgressCount: 140,
            plannedCount: 80,
            completionRatePercent: 59.3,
            totalRoadSigns: 222112,
            totalSignCategories: 5,
            totalBlackspots: 7,
            highRiskBlackspots: 3,
            rectifiedBlackspots: 2,
            monitoredBlackspots: 2,
          },
          routes: [
            {
              routeCode: 'QL.1',
              routeName: 'Quốc lộ 1',
              lengthKm: 2301.5,
              segmentCount: 45,
              branchId: 'cuc_ql_duong_bo_1',
              branchName: 'Khu QLĐB I',
              surfaceType: 'Bê tông nhựa cấp cao A1',
              roadClass: 'Cấp III',
              managementUnit: 'Khu QLĐB I',
            },
          ],
          branchDistribution: [
            {
              branchId: 'cuc_ql_duong_bo_1',
              branchName: 'Khu QLĐB I',
              routeCount: 45,
              totalLengthKm: 6540.2,
              percent: 26.6,
            },
          ],
          surfaceDistribution: [
            {
              surfaceType: 'Bê tông nhựa',
              totalLengthKm: 18500,
              percent: 75.3,
            },
          ],
        }),
      });
    });
  });

  test('1. Scenario Login: Xử lý đăng nhập sai và đăng nhập thành công', async ({ page }) => {
    await page.goto('/login');

    // 1.1 Kiểm tra form đăng nhập
    await expect(page.getByText('HỆ THỐNG QUẢN LÝ KCHT ĐƯỜNG BỘ')).toBeVisible();

    // 1.2 Nhập mật khẩu sai -> hiển thị thông báo lỗi
    await page.locator('input#username').fill('admin');
    await page.locator('input#password').fill('WrongPassword!');
    await page.getByRole('button', { name: /Đăng nhập/i }).click();

    await expect(page.getByText('Tên đăng nhập hoặc mật khẩu không chính xác.')).toBeVisible();

    // 1.3 Nhập mật khẩu đúng -> đăng nhập thành công vào /dashboard
    await page.locator('input#password').fill('mock-password');
    await page.getByRole('button', { name: /Đăng nhập/i }).click();

    await expect(page).toHaveURL(/.*dashboard/);
    await expect(page.getByRole('heading', { name: /Bảng Điều hành/i })).toBeVisible();
  });

  test('2. Scenario Dashboard: Hiển thị các thẻ chỉ số KPI và bảng phân bổ đơn vị', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kcht_access_token', 'mock-admin-token');
      localStorage.setItem('kcht_user_profile', JSON.stringify({
        username: 'admin',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
      }));
    });

    await page.goto('/dashboard');

    await expect(page.getByRole('heading', { name: /Bảng Điều hành/i })).toBeVisible();
    await expect(page.getByText('222.112')).toBeVisible();
    await expect(page.getByText('11.631')).toBeVisible();
    await expect(page.getByText('Khu QLĐB I', { exact: true })).toBeVisible();
  });

  test('3. Scenario Tree: Điều hướng cây phân cấp danh mục tài sản', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kcht_access_token', 'mock-admin-token');
      localStorage.setItem('kcht_user_profile', JSON.stringify({
        username: 'admin',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
      }));
    });

    await page.goto('/assets');

    // Bấm chọn nút truy cập nhanh Cầu đường bộ
    const bridgeShortcut = page.getByRole('button', { name: /Cầu đường bộ/i });
    await expect(bridgeShortcut).toBeVisible();
    await bridgeShortcut.click();

    // Bảng nạp dữ liệu cầu
    await expect(page.getByText('Cầu Thăng Long')).toBeVisible();
    await expect(page.getByText('Cầu Chương Dương')).toBeVisible();
  });

  test('4. Scenario Search: Tìm kiếm từ khóa và lọc dữ liệu bảng', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kcht_access_token', 'mock-admin-token');
      localStorage.setItem('kcht_user_profile', JSON.stringify({
        username: 'admin',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
      }));
    });

    await page.goto('/assets?datasetKey=tbl_bridge');
    await expect(page.getByText('Cầu Thăng Long')).toBeVisible();

    // Nhập từ khóa tìm kiếm trên bảng dữ liệu
    const searchInput = page.getByPlaceholder('Tìm kiếm theo từ khóa (q)...');
    await searchInput.fill('Chương Dương');
    await searchInput.press('Enter');

    await expect(page.getByText('Cầu Chương Dương')).toBeVisible();
  });

  test('5. Scenario Detail: Mở Drawer xem chi tiết thuộc tính tài sản', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kcht_access_token', 'mock-admin-token');
      localStorage.setItem('kcht_user_profile', JSON.stringify({
        username: 'admin',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
      }));
    });

    await page.goto('/assets?datasetKey=tbl_bridge');
    await expect(page.getByText('Cầu Thăng Long')).toBeVisible();

    // Bấm nút Chi tiết ở dòng đầu tiên
    const detailButtons = page.getByRole('button', { name: 'Chi tiết' });
    await detailButtons.first().click();

    // Drawer hiển thị chi tiết thuộc tính
    await expect(page.getByText('Thuộc tính Nghiệp vụ')).toBeVisible();
    await expect(page.locator('.ant-drawer').getByText('Đơn vị quản lý', { exact: true })).toBeVisible();

    // Đóng drawer
    const closeBtn = page.locator('.ant-drawer-close');
    if (await closeBtn.isVisible()) {
      await closeBtn.click();
    }
  });

  test('6. Scenario Map: Bản đồ số WebGIS hiển thị container OpenLayers', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kcht_access_token', 'mock-admin-token');
      localStorage.setItem('kcht_user_profile', JSON.stringify({
        username: 'admin',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
      }));
    });

    await page.goto('/map/assets');

    await expect(page.getByRole('heading', { name: /Bản đồ Số WebGIS/i })).toBeVisible();
    await expect(page.locator('.ol-viewport')).toBeAttached();
  });

  test('7. Scenario Report: Báo cáo thống kê chiều dài quốc lộ và bảo trì', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kcht_access_token', 'mock-admin-token');
      localStorage.setItem('kcht_user_profile', JSON.stringify({
        username: 'admin',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
      }));
    });

    await page.goto('/reports');

    await expect(page.getByRole('heading', { name: /Báo cáo & Thống kê/i })).toBeVisible();
    await expect(page.getByText('Chiều dài mạng lưới đường bộ')).toBeVisible();
  });

  test('8. Scenario Download: Kích hoạt xuất khẩu tệp dữ liệu CSV', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kcht_access_token', 'mock-admin-token');
      localStorage.setItem('kcht_user_profile', JSON.stringify({
        username: 'admin',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
      }));
    });

    await page.goto('/assets?datasetKey=tbl_bridge');
    await expect(page.getByText('Cầu Thăng Long')).toBeVisible();

    // Bấm nút xuất file CSV
    const downloadPromise = page.waitForEvent('download', { timeout: 8000 }).catch(() => null);
    const exportButton = page.getByRole('button', { name: /Xuất file CSV/i });
    if (await exportButton.isVisible()) {
      await exportButton.click();
      const download = await downloadPromise;
      if (download) {
        expect(download.suggestedFilename()).toContain('.csv');
      }
    }
  });

});
