import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

test.describe("Settings model registry @fullstack", () => {
  test("shows Ollama registry state and safe verify/pull controls @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/settings");

    const card = page.getByTestId("model-registry-card");
    await expect(card).toBeVisible({ timeout: 20_000 });
    await expect(card.getByRole("heading", { name: /LLM models/i })).toBeVisible({ timeout: 20_000 });
    await expect(card.getByRole("heading", { name: /Embedding models/i })).toBeVisible({ timeout: 20_000 });

    const verifyButtons = card.getByRole("button", { name: /Verify|Verificar/i });
    const pullButtons = card.getByRole("button", { name: /Pull|Descargar/i });
    await expect(verifyButtons.first()).toBeVisible({ timeout: 20_000 });
    await expect(pullButtons.first()).toBeVisible({ timeout: 20_000 });

    await verifyButtons.first().click();
    await expect(card.getByText(/Available|Missing|Error|Disponible|Falta|Error/i).first())
      .toBeVisible({ timeout: 30_000 });
  });
});
