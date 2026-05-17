import { expect, test } from "@playwright/test";
import { seedEmail, seedPassword } from "../fixtures/users";
import { authHeaders, loginAndGetToken } from "../api/fixtures/auth";
import { apiBaseUrl, productUrl } from "../api/fixtures/env";
import { createNewChatConversation, loginAsSeedUser } from "../support/helpers";

type ProjectListResponse = { items?: Array<{ id?: string; name?: string }> };

test.describe("Fullstack E2E preflight @preflight", () => {
  test("web, backend, seed auth, project seed, API base URL, and critical Chat selectors are ready", async ({
    page,
    request,
  }) => {
    expect(() => new URL(apiBaseUrl()), `API_BASE_URL must be a valid absolute URL: ${apiBaseUrl()}`).not.toThrow();

    const backendHealth = await request.get(`${apiBaseUrl()}/actuator/health`);
    expect(backendHealth.status(), await backendHealth.text()).toBe(200);

    const webResponse = await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 10_000 });
    expect(webResponse?.ok(), "web login page should be reachable").toBeTruthy();

    const token = await loginAndGetToken(request, seedEmail(), seedPassword());
    const projectsRes = await request.get(productUrl("/projects?page=0&size=10"), {
      headers: authHeaders(token),
    });
    expect(projectsRes.status(), await projectsRes.text()).toBe(200);
    const projects = (await projectsRes.json()) as ProjectListResponse;
    const firstProject = (projects.items ?? []).find((p) => p.id);
    expect(firstProject?.id, "seed user must have at least one project for fullstack E2E").toBeTruthy();

    await loginAsSeedUser(page);
    await page.goto(`/en/chat?projectId=${firstProject?.id}`, {
      waitUntil: "domcontentloaded",
      timeout: 10_000,
    });
    await expect(page.getByTestId("chat-page")).toBeVisible({ timeout: 10_000 });
    await createNewChatConversation(page);

    await expect(page.getByTestId("chat-message-input")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId("chat-send-button")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId("chat-config-trigger")).toBeEnabled({ timeout: 10_000 });
    await page.getByTestId("chat-config-trigger").click();
    const panel = page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog"));
    await expect(panel).toBeVisible({ timeout: 10_000 });
    await expect(panel.getByTestId("chat-preset-select")).toBeVisible({ timeout: 10_000 });
    await expect(panel.getByTestId("chat-llm-model-select")).toBeVisible({ timeout: 10_000 });
    await expect(panel.getByTestId("chat-classifier-select")).toBeVisible({ timeout: 10_000 });
    await panel.getByTestId("chat-config-runtime-collapsible").click();
    await expect(panel.getByTestId("chat-runtime-toggle-topK")).toBeVisible({ timeout: 10_000 });
    await expect(panel.getByTestId("chat-runtime-toggle-similarityThreshold")).toBeVisible({ timeout: 10_000 });
  });
});
