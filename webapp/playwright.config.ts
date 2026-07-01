import { defineConfig, devices, type PlaywrightTestConfig } from "@playwright/test";
import { applyDemoProxyEnvDefaults, resolveE2eBases } from "./scripts/e2e-bases.mjs";

applyDemoProxyEnvDefaults();
const { publicBase: uiBaseURL, apiBase: apiBaseURL } = resolveE2eBases();
const uiUrl = new URL(uiBaseURL);
const uiPort = uiUrl.port || (uiUrl.protocol === "https:" ? "443" : "80");
const reuseExistingServer = process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === "1";
const apiTestTimeout = Number.parseInt(
  process.env.PLAYWRIGHT_API_TEST_TIMEOUT_MS ?? process.env.PLAYWRIGHT_TEST_TIMEOUT_MS ?? "120000",
  10,
);
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
    navigationTimeout: 30000,
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
      timeout: apiTestTimeout,
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
