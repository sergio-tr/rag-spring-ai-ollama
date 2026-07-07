import { expect, test } from "@playwright/test";
import { seedEmail, seedPassword } from "../fixtures/users";
import { uniqueProjectName } from "../fixtures/projects";
import { authHeaders, loginAndGetTokens } from "../api/fixtures/auth";
import { actuatorHealthUrl, apiBaseUrl, productUrl } from "../api/fixtures/env";
import {
  createAndActivateProject,
  createNewChatConversation,
  gotoWithProxyRetry,
  loginAsSeedUser,
  openChatConfigurationPanel,
  expandChatConfigurationRuntimeSection,
  openChatForProject,
} from "../support/helpers";

type ProjectListResponse = { items?: Array<{ id?: string; name?: string }> };

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
    expect((projects.items ?? []).length, "seed user must have at least one project for fullstack E2E").toBeGreaterThan(0);

    await loginAsSeedUser(page);
    const projectId = await createAndActivateProject(page, uniqueProjectName("preflight"));
    await openChatForProject(page, projectId);
    await createNewChatConversation(page, { projectId, allowExisting: false });

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
    await expect(panel.getByTestId("chat-edit-assistant-configuration-link")).toBeVisible({ timeout: 15_000 });
    await expect(panel.getByTestId("chat-classifier-select")).toBeVisible({ timeout: 15_000 });

    const retrievalSection = panel.getByTestId("chat-retrieval-settings-section");
    await retrievalSection.scrollIntoViewIfNeeded();
    const retrievalNotApplicable = panel.getByTestId("chat-retrieval-settings-not-applicable");
    if (await retrievalNotApplicable.isVisible().catch(() => false)) {
      // Fresh projects without a READY index default to a non-retrieval preset (e.g. P0).
      await expect(retrievalNotApplicable).toBeVisible();
    } else {
      await expandChatConfigurationRuntimeSection(panel);
      await expect(panel.getByTestId("chat-runtime-toggle-similarityThreshold")).toBeVisible({
        timeout: 15_000,
      });
    }
  });
});
