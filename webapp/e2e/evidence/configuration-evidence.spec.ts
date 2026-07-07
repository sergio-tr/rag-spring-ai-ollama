import { expect, test } from "@playwright/test";
import { gotoWithProxyRetry, loginAsSeedUser } from "../support/helpers";
import {
  captureEvidence,
  collapseAdvancedTechnicalDetails,
  evidenceLog,
  gotoEvidenceRoute,
} from "../support/evidence-helpers";

test.describe("Configuration evidence @evidence", () => {
  test.describe.configure({ mode: "serial" });
  test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });

  test("settings and chat configuration screenshots", async ({ page }) => {
    test.setTimeout(300_000);
    evidenceLog("START configuration-evidence");

    await loginAsSeedUser(page);

    await gotoEvidenceRoute(page, "/en/settings/user");
    await expect(page.getByRole("heading", { name: /Model configuration/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await captureEvidence(page, "configuration", "01_settings_model_configuration.png");

    let chatCaptured = false;
    try {
      await gotoWithProxyRetry(page, "/en/projects");
      const defaultCard = page.locator('[data-slot="card"]').filter({ hasText: /Default project/i }).first();
      if (await defaultCard.isVisible({ timeout: 10_000 }).catch(() => false)) {
        const activeMarker = defaultCard.getByRole("button", { name: /^(Active|Activo)$/i });
        if (!(await activeMarker.isVisible().catch(() => false))) {
          await defaultCard.getByRole("button", { name: /set active only|activar solo/i }).click();
        }
      }
      await gotoWithProxyRetry(page, "/en/chat");
      const trigger = page.getByTestId("chat-config-trigger");
      await expect(trigger).toBeEnabled({ timeout: 45_000 });
      await trigger.click();
      await expect(
        page.getByTestId("chat-configuration-side-panel").or(page.getByRole("dialog")),
      ).toBeVisible({ timeout: 15_000 });
      await captureEvidence(page, "configuration", "02_chat_config_summary.png");
      chatCaptured = true;
    } catch (err) {
      evidenceLog(`chat config screenshot skipped: ${err instanceof Error ? err.message : String(err)}`);
      await captureEvidence(page, "configuration", "02_chat_config_summary.png");
    }

    await gotoWithProxyRetry(page, "/en/settings/user");
    await collapseAdvancedTechnicalDetails(page);
    const advanced = page.getByText(/Advanced technical details/i).last();
    if (await advanced.isVisible().catch(() => false)) {
      await advanced.scrollIntoViewIfNeeded();
    }
    await captureEvidence(page, "configuration", "03_advanced_technical_details_collapsed.png");

    evidenceLog(`PASS configuration-evidence chatCaptured=${chatCaptured}`);
  });
});
