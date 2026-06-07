import { expect, test } from "@playwright/test";
import { seedEmail, seedPassword } from "../fixtures/users";
import { authHeaders, loginAndGetTokens } from "../api/fixtures/auth";
import { actuatorHealthUrl, apiBaseUrl, productUrl } from "../api/fixtures/env";
import {
  bootstrapBrowserSession,
  gotoWithProxyRetry,
  openChatConfigurationPanel,
  expandChatConfigurationRuntimeSection,
} from "../support/helpers";

type ProjectListResponse = { items?: Array<{ id?: string; name?: string }> };
type ConversationListResponse = { items?: Array<{ id?: string }> } | Array<{ id?: string }>;

function conversationIdsFromListBody(body: ConversationListResponse): string[] {
  if (Array.isArray(body)) {
    return body.map((c) => c.id).filter(Boolean) as string[];
  }
  return (body.items ?? []).map((c) => c.id).filter(Boolean) as string[];
}

test.describe("Fullstack E2E preflight @preflight", () => {
  test("web, backend, seed auth, project seed, API base URL, and critical Chat selectors are ready", async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000);

    expect(() => new URL(apiBaseUrl()), `API_BASE_URL must be a valid absolute URL: ${apiBaseUrl()}`).not.toThrow();

    const backendLiveness = await request.get(actuatorHealthUrl("/liveness"));
    expect(backendLiveness.status(), await backendLiveness.text()).toBe(200);

    await gotoWithProxyRetry(page, "/en/login", { timeout: 45_000, maxAttempts: 3 });
    expect(page.url(), "web login page should be reachable").toMatch(/\/en\/login/);

    const tokens = await loginAndGetTokens(request, seedEmail(), seedPassword());
    const token = tokens.accessToken;
    const projectsRes = await request.get(productUrl("/projects?page=0&size=10"), {
      headers: authHeaders(token),
    });
    expect(projectsRes.status(), await projectsRes.text()).toBe(200);
    const projects = (await projectsRes.json()) as ProjectListResponse;
    const firstProject = (projects.items ?? []).find((p) => p.id);
    expect(firstProject?.id, "seed user must have at least one project for fullstack E2E").toBeTruthy();
    const projectId = firstProject!.id!;

    const convListRes = await request.get(productUrl(`/projects/${projectId}/conversations`), {
      headers: authHeaders(token),
    });
    expect(convListRes.status(), await convListRes.text()).toBe(200);
    let conversationId = conversationIdsFromListBody((await convListRes.json()) as ConversationListResponse)[0];
    if (!conversationId) {
      const createRes = await request.post(productUrl(`/projects/${projectId}/conversations`), {
        headers: { ...authHeaders(token), "Content-Type": "application/json" },
        data: {},
      });
      expect(createRes.status(), await createRes.text()).toBe(201);
      conversationId = ((await createRes.json()) as { id: string }).id;
    }
    expect(conversationId, "seed project must have a conversation for chat preflight").toBeTruthy();

    await bootstrapBrowserSession(page, tokens, { skipProjectsReady: true });

    await gotoWithProxyRetry(
      page,
      `/en/chat?projectId=${projectId}&conversationId=${conversationId}`,
      { timeout: 45_000, maxAttempts: 3 },
    );

    const composer = page
      .getByTestId("chat-readable-column")
      .getByTestId("chat-message-composer")
      .or(page.getByRole("textbox", { name: /message/i }));
    await expect(composer).toBeVisible({ timeout: 45_000 });
    await expect(composer).toBeEnabled({ timeout: 20_000 });
    await expect(page.getByTestId("chat-readable-column").getByTestId("chat-send-button")).toBeVisible({
      timeout: 15_000,
    });

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
