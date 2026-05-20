import { defineConfig, devices, type PlaywrightTestConfig } from "@playwright/test";

const uiBaseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000";
const uiUrl = new URL(uiBaseURL);
const uiPort = uiUrl.port || (uiUrl.protocol === "https:" ? "443" : "80");
const reuseExistingServer = process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === "1";
const apiBaseURL =
  process.env.API_BASE_URL ?? process.env.INTEGRATION_BACKEND_URL ?? "http://127.0.0.1:9000";
const ignoreHTTPSErrors = process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS === "1";
const retries = process.env.PLAYWRIGHT_RETRIES
  ? Number.parseInt(process.env.PLAYWRIGHT_RETRIES, 10)
  : 0;
const testTimeout = Number.parseInt(process.env.PLAYWRIGHT_TEST_TIMEOUT_MS ?? "30000", 10);
const expectTimeout = Number.parseInt(process.env.PLAYWRIGHT_EXPECT_TIMEOUT_MS ?? "10000", 10);
const maxFailures = process.env.PLAYWRIGHT_MAX_FAILURES
  ? Number.parseInt(process.env.PLAYWRIGHT_MAX_FAILURES, 10)
  : 0;
const trace = (process.env.PLAYWRIGHT_TRACE ?? (process.env.CI ? "retain-on-failure" : "on-first-retry")) as NonNullable<
  NonNullable<PlaywrightTestConfig["use"]>["trace"]
>;
/** Default cap keeps smoke specs stable against a single `next start` instance (parallel contention caused flaky auth forms). */
const workers = process.env.PLAYWRIGHT_WORKERS
  ? Number.parseInt(process.env.PLAYWRIGHT_WORKERS, 10)
  : 2;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries,
  workers,
  timeout: testTimeout,
  maxFailures,
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
    trace,
    screenshot: "only-on-failure",
    video: process.env.CI ? "off" : "on-first-retry",
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
        command: `npm run start -- -H ${uiUrl.hostname} -p ${uiPort}`,
        url: uiBaseURL,
        reuseExistingServer,
        timeout: 120_000,
      },
});
