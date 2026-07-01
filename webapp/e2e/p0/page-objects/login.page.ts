import { expect, type Page } from "@playwright/test";
import { adminEmail, adminPassword, seedEmail, seedPassword } from "../../fixtures/users";
import { loginAsE2eAdmin, loginAsSeedUser } from "../../support/helpers";

export class LoginPage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto("/en/login", { waitUntil: "domcontentloaded" });
  }

  async loginWithCredentials(email: string, password: string): Promise<void> {
    await this.goto();
    await this.page.getByLabel(/email|correo/i).fill(email);
    await this.page.getByLabel(/^password$/i).fill(password);
    await this.page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
  }

  async loginAsSeedUser(): Promise<void> {
    await loginAsSeedUser(this.page);
  }

  async loginAsAdmin(): Promise<void> {
    await loginAsE2eAdmin(this.page);
  }

  async expectInvalidCredentials(): Promise<void> {
    await expect(this.page.locator('p[role="alert"]')).toContainText(/invalid email|incorrectos/i, {
      timeout: 15_000,
    });
  }

  async expectReachedProjects(): Promise<void> {
    await expect(this.page).toHaveURL(/\/en\/projects/, { timeout: 15_000 });
    await expect(this.page.getByRole("heading", { name: /^projects$/i })).toBeVisible({ timeout: 15_000 });
  }

  static seedCredentials(): { email: string; password: string } {
    return { email: seedEmail(), password: seedPassword() };
  }

  static adminCredentials(): { email: string; password: string } {
    return { email: adminEmail(), password: adminPassword() };
  }
}
