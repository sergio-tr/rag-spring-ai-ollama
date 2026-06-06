import { expect, test } from "@playwright/test";
import { seedEmail, seedPassword } from "../fixtures/users";
import { authHeaders, loginAndGetToken } from "../api/fixtures/auth";
import { actuatorHealthUrl, apiBaseUrl, productUrl } from "../api/fixtures/env";
import { ensureChatConversationForPreflight, loginAsSeedUser, openChatConfigurationPanel, expandChatConfigurationRuntimeSection } from "../support/helpers";

type ProjectListResponse = { items?: Array<{ id?: string; name?: string }> };

test.describe("Fullstack E2E preflight @preflight", () => {
  test("web, backend, seed auth, project seed, API base URL, and critical Chat selectors are ready", async ({
    page,
    request,
  }) => {
    expect(() => new URL(apiBaseUrl()), `API_BASE_URL must be a valid absolute URL: ${apiBaseUrl()}`).not.toThrow();

    const backendLiveness = await request.get(actuatorHealthUrl("/liveness"));
    expect(backendLiveness.status(), await backendLiveness.text()).toBe(200);

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
    const projectId = firstProject!.id!;
    const convRes = await request.get(productUrl(`/projects/${projectId}/conversations`), {
      headers: authHeaders(token),
    });
    expect(convRes.status(), await convRes.text()).toBe(200);
    const existingConvs = (await convRes.json()) as Array<{ id?: string }>;
    const existingConvId = existingConvs.find((c) => c.id)?.id;

    await ensureChatConversationForPreflight(page, projectId, existingConvId);

    await expect(page.getByTestId("chat-message-composer")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("chat-send-button")).toBeVisible({ timeout: 15_000 });

    const panel = await openChatConfigurationPanel(page);
    await expect(panel.getByTestId("chat-preset-select")).toBeVisible({ timeout: 15_000 });
    await expect(panel.getByTestId("chat-llm-model-select")).toBeVisible({ timeout: 15_000 });
    await expect(panel.getByTestId("chat-classifier-select")).toBeVisible({ timeout: 15_000 });
    await expandChatConfigurationRuntimeSection(panel);
    await expect(panel.getByTestId("chat-runtime-toggle-similarityThreshold")).toBeVisible({
      timeout: 15_000,
    });
  });
});
