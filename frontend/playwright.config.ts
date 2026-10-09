import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 45000,
  expect: {
    timeout: 8000,
  },
  fullyParallel: false,
  reporter: [['list']],
  use: {
    launchOptions: process.env.QA_CHROME_PATH ? { executablePath: process.env.QA_CHROME_PATH } : {},
    baseURL: process.env.QA_BASE_URL || 'http://localhost:3000',
    trace: 'off',
    screenshot: 'only-on-failure',
    viewport: { width: 1366, height: 768 },
    actionTimeout: 10000,
    navigationTimeout: 15000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: process.env.QA_BASE_URL || 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 30000,
  },
});
