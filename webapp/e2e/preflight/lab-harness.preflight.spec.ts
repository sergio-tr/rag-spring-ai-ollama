import { expect, test } from "@playwright/test";
import { preflightLabE2eHarness } from "../support/lab-harness";

const evidenceDir =
  process.env.E2E_HARNESS_EVIDENCE_DIR ??
  process.env.E2E_EVIDENCE_DIR ??
  ".cursor/evidence/wave-1-current/e2e";

test.describe("LAB E2E harness preflight @preflight @lab-harness", () => {
  test("proxy/web/backend/auth/active-jobs cleanup/LAB route", async ({ page, request }) => {
    const log = await preflightLabE2eHarness(page, request, evidenceDir);
    expect(log.some((line) => line.includes("GET /lab/jobs/active OK"))).toBe(true);
    expect(log.some((line) => line.includes("no blocking active jobs"))).toBe(true);
    expect(log.some((line) => line.includes("LAB overview page OK"))).toBe(true);
  });
});
