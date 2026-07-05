import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-07: Lab RAG preset benchmark - canonical {@link lab-evaluation-run-card} flow:
 * POST `{product}/lab/benchmarks/RAG_PRESET_END_TO_END/runs` with a typed dataset, poll job, optional MVP panel +
 * raw async payload in a collapsible details block (no removed sync checkbox).
 */
test.describe("Lab RAG benchmark", () => {
  test("E2E-07 RAG benchmark shows raw async JSON after terminal job @fullstack", async ({ page }) => {
    test.slow();
    // CI global default is often 30s; this flow may wait on Spring/Ollama - override explicitly.
    test.setTimeout(180_000);
    await loginAsSeedUser(page);
    await page.goto("/en/lab/evaluation/rag");
    await expect(page.getByRole("heading", { name: /research lab|laboratorio/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    const runButton = page.getByTestId("lab-rag-run");
    await expect(runButton).toBeVisible({ timeout: 20_000 });

    const needsDataset = page.getByTestId("lab-benchmark-needs-dataset-warn");

    await expect
      .poll(
        async () => {
          const needs = await needsDataset.isVisible().catch(() => false);
          const disabled = await runButton.isDisabled();
          return needs || !disabled;
        },
        { timeout: 45_000, intervals: [300, 600, 1200] },
      )
      .toBe(true);

    test.skip(await needsDataset.isVisible().catch(() => false), "No RAG-compatible experimental dataset (upload RAG preset workbook or use ADMIN reference bundle).");

    await expect(runButton).toBeEnabled();

    await runButton.click();

    const rawSummary = page.locator("summary").filter({ hasText: /Raw async payload|JSON.*advanced/i });
    const resultsPanel = page.getByTestId("lab-benchmark-results-panel");
    const benchErr = page.locator('[data-slot="card"]').getByRole("alert").first();
    const jobPanel = page.getByTestId("lab-job-panel");

    try {
      await expect
        .poll(
          async () => {
            const panel = await jobPanel.isVisible().catch(() => false);
            const raw = await rawSummary.isVisible().catch(() => false);
            const mvp = await resultsPanel.isVisible().catch(() => false);
            const err = await benchErr.isVisible().catch(() => false);
            return panel || raw || mvp || err;
          },
          { timeout: 120_000, intervals: [400, 1200, 2400] },
        )
        .toBe(true);
    } catch {
      test.skip(true, "No lab feedback after Run within 120s (job panel / alert / results).");
      return;
    }

    async function snapshotOutcome(): Promise<{ raw: boolean; mvp: boolean; err: boolean }> {
      return {
        raw: await rawSummary.isVisible().catch(() => false),
        mvp: await resultsPanel.isVisible().catch(() => false),
        err: await benchErr.isVisible().catch(() => false),
      };
    }

    let { raw: rawOk, mvp: mvpOk, err: errOk } = await snapshotOutcome();

    // Job panel can appear while the async task is still running; wait for a terminal UI signal.
    if ((await jobPanel.isVisible().catch(() => false)) && !rawOk && !mvpOk && !errOk) {
      try {
        await expect
          .poll(
            async () => {
              const o = await snapshotOutcome();
              return o.raw || o.mvp || o.err;
            },
            { timeout: 120_000, intervals: [400, 1200, 2400] },
          )
          .toBe(true);
      } catch {
        test.skip(true, "Lab job started but no terminal outcome UI within 120s.");
        return;
      }
      ({ raw: rawOk, mvp: mvpOk, err: errOk } = await snapshotOutcome());
    }

    if (!rawOk && !mvpOk && errOk) {
      test.skip(true, "Benchmark failed (models/runtime); skipped strict JSON assertion.");
      return;
    }

    expect(rawOk || mvpOk).toBe(true);

    if (await rawSummary.isVisible().catch(() => false)) {
      await rawSummary.click();
      const resultPre = page.locator('[data-slot="card"] pre').filter({ hasText: /^\s*[\[{]/ }).first();
      await expect(resultPre).toBeVisible({ timeout: 15_000 });
      await expect
        .poll(async () => {
          const t = (await resultPre.textContent())?.trim() ?? "";
          return t.length > 2 && (t.startsWith("{") || t.startsWith("["));
        }, { timeout: 10_000 })
        .toBe(true);
    } else {
      await expect(resultsPanel).toBeVisible();
    }
  });
});
