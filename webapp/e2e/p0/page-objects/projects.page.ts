import { expect, type Page } from "@playwright/test";
import { createAndActivateProject } from "../../support/helpers";

export class ProjectsPage {
  constructor(readonly page: Page) {}

  async expectLoaded(): Promise<void> {
    await expect(this.page.getByRole("heading", { name: /^projects$/i })).toBeVisible({ timeout: 15_000 });
  }

  async openNewProjectDialog(): Promise<void> {
    const mainNewProject = this.page.locator("main").getByRole("button", { name: /new project|nuevo proyecto/i }).first();
    const trigger = (await mainNewProject.isVisible().catch(() => false))
      ? mainNewProject
      : this.page.getByRole("button", { name: /new project|nuevo proyecto/i }).first();
    await expect(trigger).toBeEnabled({ timeout: 15_000 });
    await trigger.click();
    const dialog = this.page.getByTestId("new-project-dialog").or(this.page.getByRole("dialog"));
    await expect(dialog).toBeVisible();
  }

  async createProject(name: string): Promise<string> {
    return createAndActivateProject(this.page, name);
  }

  async expectNoCreateError(): Promise<void> {
    await expect(this.page.getByTestId("project-create-error")).toHaveCount(0);
  }

  async expectProjectActive(name: string): Promise<void> {
    const card = this.page.locator('[data-slot="card"]').filter({ hasText: name }).first();
    await expect(card).toBeVisible({ timeout: 20_000 });
    await expect(card.getByRole("button", { name: /^(Active|Activo)$/i })).toBeVisible({ timeout: 20_000 });
  }

  async navigateViaSidebar(label: RegExp): Promise<void> {
    await this.page.getByRole("link", { name: label }).click();
  }
}
