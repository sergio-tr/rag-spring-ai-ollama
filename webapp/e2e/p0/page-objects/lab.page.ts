import { expect, type Page } from "@playwright/test";

export type LabEvaluationSegment = "rag" | "llm" | "embedding";

export class LabPage {
  constructor(readonly page: Page) {}

  async openEvaluation(segment: LabEvaluationSegment): Promise<void> {
    await this.page.goto(`/en/lab/evaluation/${segment}`, { waitUntil: "domcontentloaded", timeout: 15_000 });
    await expect(this.page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
      timeout: 20_000,
    });
  }

  async openClassifier(): Promise<void> {
    await this.page.goto("/en/lab/classifier", { waitUntil: "domcontentloaded", timeout: 20_000 });
    await expect(this.page.getByText(/classifier|clasificador/i).first()).toBeVisible({ timeout: 15_000 });
  }

  async expectClassifierTrainReadyOrUnavailable(): Promise<void> {
    const unavailableNotice = this.page.getByText(
      /Classifier is not configured in the backend|El clasificador no está configurado en el backend/i,
    );
    const registry = this.page.getByTestId("classifier-registry-table");
    await expect
      .poll(
        async () =>
          (await unavailableNotice.isVisible().catch(() => false)) ||
          (await registry.isVisible().catch(() => false)),
        { timeout: 15_000, intervals: [250, 500, 1000] },
      )
      .toBe(true);

    if (await registry.isVisible().catch(() => false)) {
      await expect(this.page.getByTestId("lab-classifier-train")).toBeEnabled();
      await expect(this.page.getByTestId("lab-classifier-train-model-name")).toBeVisible();
      await expect(this.page.getByTestId("lab-classifier-train-file")).toBeAttached();
    } else {
      await expect(unavailableNotice).toBeVisible();
      await expect(this.page.getByTestId("lab-classifier-train")).toBeDisabled();
    }
  }
}
