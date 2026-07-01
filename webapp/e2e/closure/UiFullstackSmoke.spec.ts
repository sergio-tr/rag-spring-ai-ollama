import { expect, test } from "@playwright/test";
import {
  runUiFullstackSmokeChecks,
  writeUiFullstackSmokeMd,
} from "../fixtures/ui-fullstack-smoke";

test.describe("Phase 7 UI fullstack smoke @fullstack @uiSmoke", () => {
  test("validates S3 UX surfaces on real stack and writes evidence", async ({ page, request }) => {
    test.setTimeout(900_000);
    const result = await runUiFullstackSmokeChecks(page, request);
    writeUiFullstackSmokeMd(result);
    expect(result.allPass, JSON.stringify(result.checks, null, 2)).toBe(true);
  });
});
