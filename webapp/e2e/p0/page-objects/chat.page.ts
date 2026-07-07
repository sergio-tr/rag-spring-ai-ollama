import { expect, type Locator, type Page } from "@playwright/test";
import {
  createNewChatConversation,
  openChatConfigurationPanel,
  openChatForProject,
  sendChatMessage,
} from "../../support/helpers";

export class ChatPage {
  constructor(readonly page: Page) {}

  async openForProject(projectId: string): Promise<void> {
    await openChatForProject(this.page, projectId);
  }

  async createConversation(projectId?: string): Promise<string> {
    return createNewChatConversation(this.page, projectId ? { projectId } : undefined);
  }

  async openConfigurationPanel(): Promise<Locator> {
    return openChatConfigurationPanel(this.page);
  }

  async selectPresetByPattern(panel: Locator, pattern: RegExp): Promise<void> {
    const presetSelect = panel.getByTestId("chat-preset-select");
    await expect(presetSelect).toBeVisible({ timeout: 15_000 });
    const options = await presetSelect.locator("option").evaluateAll((opts) =>
      opts.map((o) => ({
        value: (o as HTMLOptionElement).value,
        text: (o.textContent ?? "").trim(),
        disabled: (o as HTMLOptionElement).disabled,
      })),
    );
    const match = options.find((o) => !o.disabled && pattern.test(o.text));
    if (match?.value) {
      await presetSelect.selectOption(match.value);
      await expect(presetSelect).toHaveValue(match.value);
    }
  }

  async sendMessage(message: string, timeoutMs = 30_000): Promise<void> {
    await sendChatMessage(this.page, message, {
      textareaReadyTimeoutMs: timeoutMs,
      sendEnabledTimeoutMs: timeoutMs,
    });
  }

  async expectAnswerVisible(timeoutMs = 180_000): Promise<void> {
    await expect(this.page.getByTestId("chat-answer")).toBeVisible({ timeout: timeoutMs });
    await expect(this.page.getByText(/\[PENDING\]|Processing/i)).toHaveCount(0);
  }

  async expectSourcesVisible(timeoutMs = 30_000): Promise<void> {
    await expect(this.page.getByTestId("chat-sources")).toBeVisible({ timeout: timeoutMs });
  }
}
