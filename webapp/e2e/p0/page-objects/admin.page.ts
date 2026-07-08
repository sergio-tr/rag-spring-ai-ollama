import { expect, type Page } from "@playwright/test";

export class AdminPage {
  constructor(readonly page: Page) {}

  async open(): Promise<void> {
    await expect(this.page.getByRole("link", { name: /^admin$/i })).toBeVisible();
    await this.page.goto("/en/admin");
    await expect(this.page.getByRole("heading", { name: /administration|administración/i })).toBeVisible({
      timeout: 20_000,
    });
  }

  async expectModelCatalogLoaded(): Promise<void> {
    const modelAdmin = this.page.getByTestId("admin-models-card");
    await expect(modelAdmin).toBeVisible({ timeout: 15_000 });
    await expect(modelAdmin.getByText(/configured model catalog|catálogo de modelos configurado/i)).toBeVisible();
    await expect(modelAdmin.getByText(/llm models|modelos llm/i)).toBeVisible();
    await expect(modelAdmin.getByText(/embedding models|modelos de embedding/i)).toBeVisible();
    await expect(this.page.getByText(/could not load model catalog|no se pudo cargar el catálogo/i)).toHaveCount(0);
  }
}
