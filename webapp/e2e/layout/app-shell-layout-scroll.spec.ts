import fs from "node:fs";
import path from "node:path";
import { expect, test } from "@playwright/test";
import {
  LAYOUT_SMOKE_CONVERSATION_ID,
  LAYOUT_SMOKE_PROJECT_ID,
  measureAppScrollOwners,
} from "../support/layout-helpers";
import {
  addSmokeAccessCookie,
  installLayoutProductApiStub,
  seedLayoutActiveProject,
} from "../smoke/fixtures/layout-product-api-stub";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.cursor/evidence/wave-1-current/p1-01",
);

test.describe("App shell layout scroll @layout @scroll", () => {
  test.use({ viewport: { width: 1366, height: 768 } });

  test.beforeAll(() => {
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  });

  test.beforeEach(async ({ page }) => {
    await installLayoutProductApiStub(page);
    await seedLayoutActiveProject(page);
    await addSmokeAccessCookie(page);
  });

  test("LAB: document scroll owner, no viewport void @layout @scroll", async ({ page }) => {
    await page.goto("/en/lab", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /research lab|laboratorio de investigación/i })).toBeVisible({
      timeout: 30_000,
    });

    const metrics = await measureAppScrollOwners(page);
    expect(metrics.mainScrollMode).toBe("document");
    expect(metrics.mainOverflowY).toMatch(/auto|scroll/);
    expect(metrics.documentScrollable).toBe(false);
    expect(metrics.bodyScrollable).toBe(false);
    expect(metrics.voidBelowMainPx).toBeLessThanOrEqual(8);

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "layout-lab-1366x768.png"),
      fullPage: false,
    });
  });

  test("Chat: main locked, thread may scroll, no black void @layout @scroll", async ({ page }) => {
    const pageErrors: string[] = [];
    page.on("pageerror", (err) => {
      pageErrors.push(err.message);
      console.error("[layout-scroll] pageerror:", err.message);
    });

    await page.goto(
      `/en/chat?projectId=${LAYOUT_SMOKE_PROJECT_ID}&conversationId=${LAYOUT_SMOKE_CONVERSATION_ID}`,
      { waitUntil: "domcontentloaded", timeout: 60_000 },
    );

    await expect(page.getByTestId("chat-page")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId(`conversation-item-${LAYOUT_SMOKE_CONVERSATION_ID}`)).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByTestId("chat-thread-dropzone")).toBeVisible({ timeout: 30_000 });
    expect(pageErrors, "Chat page must not throw client-side exceptions").toEqual([]);

    const metrics = await measureAppScrollOwners(page);
    expect(metrics.mainScrollMode).toBe("chat-locked");
    expect(metrics.mainOverflowY).toBe("hidden");
    expect(metrics.mainScrollable).toBe(false);
    expect(metrics.documentScrollable).toBe(false);
    expect(metrics.bodyScrollable).toBe(false);
    expect(metrics.voidBelowChatPx).toBeLessThanOrEqual(8);
    expect(metrics.voidBelowMainPx).toBeLessThanOrEqual(8);

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "layout-chat-1366x768.png"),
      fullPage: false,
    });
  });
});
