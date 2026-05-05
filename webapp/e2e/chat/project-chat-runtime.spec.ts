import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import {
  assertChatAlertsAreSanitized,
  waitForLatestAssistantNonEmpty,
} from "../support/chat-assistant";
import {
  createAndActivateProject,
  loginAsSeedUser,
  sendChatMessage,
} from "../support/helpers";

test.describe("Project chat runtime (plan hardening) @fullstack @chatRuntime", () => {
  test("chat UI visible, preset not None, readable layout width", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-chat-ui"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page.getByTestId("chat-new-conversation").click();

    await expect(
      page.getByRole("main").getByRole("button", { name: /^send$|^enviar$/i }),
    ).toBeVisible({ timeout: 15_000 });

    await expect(page.getByTestId("chat-readable-column")).toBeVisible();

    await page.getByTestId("chat-actions-menu-trigger").click();
    await expect(page.getByRole("button", { name: /^delete chat$/i })).toBeVisible({ timeout: 15_000 });

    const preset = page.getByRole("combobox", { name: /preset/i });
    await expect(preset).toBeVisible({ timeout: 15_000 });
    await expect(preset).not.toHaveValue("");
    const selectedLabel = await preset.locator("option:checked").textContent();
    expect(selectedLabel?.toLowerCase().trim()).not.toBe("none");
    expect(selectedLabel?.toLowerCase().trim()).not.toBe("ninguno");
  });

  test("Buenos dias: no gateway failure, assistant or fallback, alerts sanitized", async ({ page }) => {
    let chatPostStatus = 0;
    page.on("response", async (res) => {
      const req = res.request();
      if (req.method() !== "POST") return;
      const path = new URL(res.url()).pathname.replace(/\/$/, "");
      if (!/\/conversations\/[^/]+\/messages$/.test(path)) return;
      chatPostStatus = res.status();
    });

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-buenos"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page.getByTestId("chat-new-conversation").click();

    await sendChatMessage(page, "Buenos dias");
    await expect(page.getByText("Buenos dias")).toBeVisible({ timeout: 15_000 });

    await expect
      .poll(() => chatPostStatus, { timeout: 30_000 })
      .toBeGreaterThan(0);
    expect([500, 502]).not.toContain(chatPostStatus);

    await expect(page.getByText(/could not send message|no se pudo enviar/i)).toHaveCount(0);

    const assistantText = await waitForLatestAssistantNonEmpty(page, 180_000);
    expect(assistantText.length).toBeGreaterThan(2);
    expect(assistantText).not.toMatch(/<\s*html/i);

    await assertChatAlertsAreSanitized(page);

    const bodyText = await page.getByRole("main").innerText();
    expect(bodyText).not.toMatch(/502\s+bad\s+gateway/i);
    expect(bodyText).not.toMatch(/<\s*html/i);
  });

  test("empty project: Buenos dias completes without send error strip", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-empty-proj"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page.getByTestId("chat-new-conversation").click();

    await sendChatMessage(page, "Buenos dias");

    await expect(page.getByText(/could not send message|no se pudo enviar/i)).toHaveCount(0, {
      timeout: 60_000,
    });

    await waitForLatestAssistantNonEmpty(page, 180_000);
    await assertChatAlertsAreSanitized(page);
  });

  test("limit retrieval checkbox toggles once and stays stable after navigation", async ({ page }) => {
    test.setTimeout(120_000);
    await loginAsSeedUser(page);
    const projectName = uniqueProjectName("e2e-limit-docs");
    await createAndActivateProject(page, projectName);

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
    await page.locator('input[type="file"]').setInputFiles({
      name: "e2e-limit-docs.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("Limit retrieval checkbox needs at least one READY document.\n"),
    });
    await expect
      .poll(
        async () => {
          const row = page.locator("tbody tr").filter({ hasText: "e2e-limit-docs.txt" });
          if ((await row.count()) === 0) return false;
          return (await row.getByText("READY", { exact: true }).count()) > 0;
        },
        { timeout: 120_000 },
      )
      .toBe(true);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await page.getByTestId("chat-new-conversation").click();

    await page.getByTestId("chat-actions-menu-trigger").click();
    const limitCb = page.getByRole("checkbox", {
      name: /limit retrieval to selected documents|limitar la recuperación/i,
    });
    await expect(limitCb).toBeVisible({ timeout: 15_000 });
    await expect(limitCb).not.toBeChecked();

    await limitCb.click();
    // Controlled checkbox: checked only after refetch + PATCH apply documentFilter (async).
    await expect
      .poll(async () => limitCb.isChecked(), { timeout: 45_000, intervals: [400, 800, 1600] })
      .toBe(true);

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    const sidebarButtons = page.locator("aside div.flex.max-h-48").getByRole("button");
    await expect(sidebarButtons.first()).toBeVisible({ timeout: 15_000 });
    await sidebarButtons.first().click();

    await page.getByTestId("chat-actions-menu-trigger").click();
    const limitAgain = page.getByRole("checkbox", {
      name: /limit retrieval to selected documents|limitar la recuperación/i,
    });
    await expect
      .poll(async () => limitAgain.isChecked(), { timeout: 20_000, intervals: [300, 600, 1200] })
      .toBe(true);
  });
});

test.describe("Nginx same-origin chat @fullstack @nginx @chatRuntime", () => {
  test.beforeEach(() => {
    test.skip(
      process.env.E2E_VIA_NGINX !== "1",
      "Set E2E_VIA_NGINX=1 and PLAYWRIGHT_BASE_URL to the nginx entry (e.g. http://127.0.0.1) with same-origin /api.",
    );
  });

  test("chat POST uses page origin (no direct :3000 API mismatch)", async ({ page, baseURL }) => {
    expect(baseURL).toBeTruthy();
    const origins = new Set<string>();
    page.on("requestfinished", (req) => {
      if (req.method() !== "POST") return;
      const u = req.url().split("?")[0];
      if (!/\/conversations\/[^/]+\/messages$/.test(u)) return;
      origins.add(new URL(req.url()).origin);
    });

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-nginx"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await page.getByTestId("chat-new-conversation").click();

    await sendChatMessage(page, "Buenos dias");
    await expect(page.getByText("Buenos dias")).toBeVisible({ timeout: 30_000 });

    await expect
      .poll(() => origins.size, { timeout: 60_000 })
      .toBeGreaterThan(0);
    const expectedOrigin = new URL(baseURL as string).origin;
    expect([...origins].every((o) => o === expectedOrigin)).toBe(true);

    await expect(page.getByText(/could not send message|no se pudo enviar/i)).toHaveCount(0, {
      timeout: 90_000,
    });
  });
});

test.describe("Classifier unavailable (manual ops) @fullstack @manual @chatRuntime", () => {
  test.beforeEach(() => {
    test.skip(
      process.env.E2E_MANUAL_CLASSIFIER_DOWN !== "1",
      "Manual only: stop classifier-service (or point RAG_CLASSIFIER_SERVICE_URL to a dead host), " +
        "set E2E_MANUAL_CLASSIFIER_DOWN=1, then rerun to validate graceful degradation.",
    );
  });

  test("Buenos dias still yields assistant text when classifier is down", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-class-down"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await page.getByTestId("chat-new-conversation").click();

    await sendChatMessage(page, "Buenos dias");
    await waitForLatestAssistantNonEmpty(page, 180_000);
    await assertChatAlertsAreSanitized(page);
  });
});
