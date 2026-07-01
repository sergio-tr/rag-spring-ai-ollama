import { expect, type Page } from "@playwright/test";
import { waitForDocumentReadyByName } from "../../support/helpers";

export class DocumentsPage {
  constructor(readonly page: Page) {}

  async openFromSidebar(): Promise<void> {
    await this.page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(this.page).toHaveURL(/\/en\/documents/);
    await expect(this.page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
  }

  async uploadFile(filePath: string): Promise<void> {
    await this.page.locator('input[type="file"]').setInputFiles(filePath);
  }

  async waitForReady(fileName: string, timeoutMs = 120_000): Promise<void> {
    await waitForDocumentReadyByName(this.page, fileName, timeoutMs);
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page.getByRole("heading", { name: /^documents$/i })).toBeVisible({ timeout: 15_000 });
  }
}
