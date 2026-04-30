import { defineConfig, devices } from "@playwright/test";

const uiBaseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000";
const apiBaseURL =
  process.env.API_BASE_URL ?? process.env.INTEGRATION_BACKEND_URL ?? "http://127.0.0.1:9000";
const ignoreHTTPSErrors = process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS === "1";
const retries = process.env.PLAYWRIGHT_RETRIES
  ? Number.parseInt(process.env.PLAYWRIGHT_RETRIES, 10)
  : process.env.CI
    ? 1
    : 0;
const testTimeout = Number.parseInt(process.env.PLAYWRIGHT_TEST_TIMEOUT_MS ?? "30000", 10);
const expectTimeout = Number.parseInt(process.env.PLAYWRIGHT_EXPECT_TIMEOUT_MS ?? "10000", 10);

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries,
  timeout: testTimeout,
  expect: {
    timeout: expectTimeout,
  },
  reporter: process.env.CI
    ? [
        ["github"],
        ["html", { outputFolder: "playwright-report", open: "never" }],
        ["list"],
      ]
    : [["list"]],
  use: {
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    ignoreHTTPSErrors,
  },
  projects: [
    {
      name: "chromium",
      testIgnore: ["**/api/**"],
      use: {
        baseURL: uiBaseURL,
        ...devices["Desktop Chrome"],
      },
    },
    {
      name: "api",
      testMatch: ["**/api/**/*.spec.ts"],
      use: {
        baseURL: apiBaseURL,
      },
    },
  ],
  webServer: process.env.PLAYWRIGHT_SKIP_WEBSERVER
    ? undefined
    : {
        command: "npm run start",
        url: uiBaseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
