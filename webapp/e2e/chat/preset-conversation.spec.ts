import { expect, test } from "@playwright/test";
import {
  authHeadersFromPage,
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
  productApiUrl,
} from "../support/helpers";
import { uniqueProjectName } from "../fixtures/projects";

/** System preset from Flyway V18 (Demo_Worst). */
const DEMO_WORST_PRESET_ID = "cafe0001-0001-4001-8001-000000000001";

/**
 * E2E-06: assign a RAG preset to the active conversation via product API (PATCH),
 * after creating a conversation from the chat UI.
 */
test.describe("Preset on conversation", () => {
  test("E2E-06 patch conversation with Demo_Worst preset @fullstack", async ({ page }) => {
    const projectName = uniqueProjectName("e2e-pres");
    await loginAsSeedUser(page);
    await createAndActivateProject(page, projectName);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);
    await createNewChatConversation(page);
    const textarea = page.getByPlaceholder(/message|mensaje/i);
    await expect(textarea).toBeEnabled({ timeout: 15_000 });

    // Avoid racing GET /conversations against POST create — UI navigates with conversationId in query.
    await expect(page).toHaveURL(/[?&]conversationId=[a-f0-9-]{36}/i, { timeout: 15_000 });
    const conversationId = new URL(page.url()).searchParams.get("conversationId");
    expect(conversationId).toBeTruthy();

    const headers = await authHeadersFromPage(page);

    const patchRes = await page.request.patch(productApiUrl(`/conversations/${conversationId}`), {
      headers: { ...headers, "Content-Type": "application/json" },
      data: JSON.stringify({ presetId: DEMO_WORST_PRESET_ID }),
    });
    expect(patchRes.ok()).toBeTruthy();
    const updated = (await patchRes.json()) as { presetId: string | null };
    expect(updated.presetId).toBe(DEMO_WORST_PRESET_ID);
  });
});
