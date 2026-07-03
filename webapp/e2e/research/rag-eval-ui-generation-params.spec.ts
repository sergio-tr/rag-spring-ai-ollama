import { expect, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { loginAsSeedUser } from "../support/helpers";

const evidenceRoot = path.resolve(
  __dirname,
  "../../../../.cursor/evidence/rag-evaluation-ui-config-cleanup-20250703/screenshots",
);

test.describe("RAG evaluation UI generation params visibility", () => {
  test.beforeAll(() => {
    fs.mkdirSync(evidenceRoot, { recursive: true });
  });

  test("RAG route hides generation parameters and LLM selector @evidence", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/rag", { waitUntil: "domcontentloaded", timeout: 30_000 });
    await expect(page.getByTestId("lab-embedding-retrieval-parameters-section")).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.getByTestId("lab-rag-task-llm-callout")).toBeVisible();
    await expect(page.getByRole("link", { name: /User settings/i })).toHaveAttribute(
      "href",
      /\/settings\/user$/,
    );
    await expect(page.getByTestId("lab-generation-parameters-section")).toHaveCount(0);
    await expect(page.getByTestId("lab-hp-temperature")).toHaveCount(0);
    await expect(page.getByText("Generation parameters", { exact: true })).toHaveCount(0);
    await expect(page.getByTestId("lab-benchmark-llm-model")).toHaveCount(0);
    await expect(page.getByText("Primary model snapshot / campaign label")).toHaveCount(0);
    await expect(page.getByText(/campaign label/i)).toHaveCount(0);
    await expect(page.getByTestId("lab-benchmark-embedding-model")).toBeVisible();
    await expect(page.getByTestId("lab-hp-top-k")).toBeVisible();
    await page.screenshot({ path: path.join(evidenceRoot, "rag-evaluation-after-fix.png"), fullPage: true });
  });

  test("LLM route shows generation parameters @evidence", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/llm", { waitUntil: "domcontentloaded", timeout: 30_000 });
    await expect(page.getByTestId("lab-hyperparameters-form")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("lab-hp-temperature")).toBeVisible();
    await page.screenshot({ path: path.join(evidenceRoot, "llm-evaluation-generation-visible.png"), fullPage: true });
  });

  test("Embedding route has retrieval without generation @evidence", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/embedding", { waitUntil: "domcontentloaded", timeout: 30_000 });
    await expect(page.getByTestId("lab-hyperparameters-form")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("lab-hp-top-k")).toBeVisible();
    await expect(page.getByTestId("lab-hp-temperature")).toHaveCount(0);
    await page.screenshot({
      path: path.join(evidenceRoot, "embedding-evaluation-no-generation.png"),
      fullPage: true,
    });
  });
});
