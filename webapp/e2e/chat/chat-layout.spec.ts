import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import {
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
} from "../support/helpers";

test.describe("Chat layout @fullstack @chatRuntime", () => {
  test("configuration panel toggles centered vs split layout on desktop", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-chat-layout"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);
    await createNewChatConversation(page);

    const workspace = page.getByTestId("chat-main-workspace");
    const readable = page.getByTestId("chat-readable-column");
    await expect(workspace).toBeVisible({ timeout: 15_000 });
    await expect(readable).toBeVisible();

    await expect(workspace).toHaveAttribute("data-chat-layout-mode", "centered");
    await expect(page.getByTestId("chat-configuration-side-panel")).toHaveCount(0);

    const workspaceBoxClosed = await workspace.boundingBox();
    const readableBoxClosed = await readable.boundingBox();
    expect(workspaceBoxClosed).not.toBeNull();
    expect(readableBoxClosed).not.toBeNull();
    if (workspaceBoxClosed && readableBoxClosed) {
      expect(readableBoxClosed.width).toBeLessThan(workspaceBoxClosed.width * 0.95);
    }

    await page.getByTestId("chat-config-trigger").click();
    const panel = page.getByTestId("chat-configuration-side-panel");
    await expect(panel).toBeVisible({ timeout: 15_000 });
    await expect(workspace).toHaveAttribute("data-chat-layout-mode", "split");

    const readableBoxOpen = await readable.boundingBox();
    const panelBoxOpen = await panel.boundingBox();
    expect(readableBoxOpen).not.toBeNull();
    expect(panelBoxOpen).not.toBeNull();
    if (readableBoxOpen && panelBoxOpen) {
      expect(readableBoxOpen.x + readableBoxOpen.width).toBeLessThanOrEqual(panelBoxOpen.x + 2);
    }

    await page.getByTestId("chat-config-trigger").click();
    await expect(panel).toHaveCount(0);
    await expect(workspace).toHaveAttribute("data-chat-layout-mode", "centered");

    const readableBoxReclosed = await readable.boundingBox();
    expect(readableBoxReclosed).not.toBeNull();
    if (workspaceBoxClosed && readableBoxReclosed) {
      expect(readableBoxReclosed.width).toBeLessThan(workspaceBoxClosed.width * 0.95);
    }
  });
});
