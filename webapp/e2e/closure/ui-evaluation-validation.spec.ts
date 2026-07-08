import { expect, type Page, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { authHeadersFromPage, createAndActivateProject, createNewChatConversation, loginAsSeedUser, openChatConfigurationPanel, productApiUrl } from "../support/helpers";
import { uniqueProjectName } from "../fixtures/projects";
import {
  assertLabDatasetControlsVisible,
  ensureFirstLlmModelSelectedForRun,
  ensureLabEvaluationCorpusReadyViaApi,
  fetchSelectableEmbeddingModelIds,
  gotoLabEvaluationPage,
  labDatasetRunnable,
  pollLabTerminalOutcome,
  prepareLabE2eTest,
  selectEmbeddingModelsByIds,
} from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  process.env.PHASE8_UI_EVIDENCE_DIR ??
    path.join(process.cwd(), "../../../docs/evidence/final-engineering-hardening-20260626/08_ui_evaluation_validation"),
);

async function installDockerWebappApiProxy(page: Page): Promise<void> {
  const backend = new URL(process.env.API_BASE_URL ?? "http://127.0.1:9000");
  await page.route("**/api/v5/**", async (route) => {
    const req = route.request();
    const target = new URL(req.url());
    target.protocol = backend.protocol;
    target.hostname = backend.hostname;
    target.port = backend.port;
    const headers = { ...req.headers() };
    delete headers.origin;
    delete headers.referer;
    const isSse =
      target.pathname.includes("/events") ||
      (headers.accept ?? "").includes("text/event-stream");
    if (isSse) {
      await route.continue({ url: target.toString(), headers });
      return;
    }
    const response = await route.fetch({
      url: target.toString(),
      method: req.method(),
      headers,
      postData: req.postDataBuffer() ?? undefined,
    });
    await route.fulfill({ response });
  });
}
type FlowEvidence = {
  flow: string;
  path: string;
  steps: string[];
  datasetLabel?: string;
  modelOrEmbedding?: string;
  presetSelection?: string;
  outcome: string;
  pass: boolean;
  issues: string[];
  screenshot?: string;
  artifactPath?: string;
};

function writeEvidence(name: string, payload: unknown): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  const p = path.join(EVIDENCE_DIR, name);
  if (typeof payload === "string") {
    fs.writeFileSync(p, payload);
  } else {
    fs.writeFileSync(p, `${JSON.stringify(payload, null, 2)}\n`);
  }
}

async function screenshot(page: Page, fileName: string): Promise<string> {
  const dir = path.join(EVIDENCE_DIR, "screenshots");
  fs.mkdirSync(dir, { recursive: true });
  const rel = path.join("screenshots", fileName);
  await page.screenshot({ path: path.join(EVIDENCE_DIR, rel), fullPage: true });
  return rel;
}

async function datasetLabel(page: Page): Promise<string> {
  const select = page.getByTestId("lab-benchmark-dataset-select");
  const value = await select.inputValue().catch(() => "");
  const label = await select.locator(`option[value="${value}"]`).textContent().catch(() => value);
  return (label ?? value).trim();
}

async function runBenchmarkFlow(
  page: Page,
  segment: "llm" | "embedding" | "rag",
  runTestId: string,
  configure?: (page: Page) => Promise<void>,
): Promise<FlowEvidence> {
  const steps: string[] = [];
  const issues: string[] = [];
  const flowPath = `/en/lab/evaluation/${segment}`;

  steps.push(`Navigate to ${flowPath}`);
  await gotoLabEvaluationPage(page, segment);
  await screenshot(page, `${segment}-01-page-loaded.png`);

  steps.push("Assert dataset controls visible");
  await assertLabDatasetControlsVisible(page);

  const needsDataset = await page.getByTestId("lab-benchmark-needs-dataset-warn").isVisible().catch(() => false);
  if (needsDataset) {
    issues.push("No compatible dataset - lab-benchmark-needs-dataset-warn visible");
    await screenshot(page, `${segment}-needs-dataset.png`);
    return {
      flow: segment.toUpperCase(),
      path: flowPath,
      steps,
      outcome: "BLOCKED_NO_DATASET",
      pass: false,
      issues,
      screenshot: `screenshots/${segment}-needs-dataset.png`,
    };
  }

  const runnable = await labDatasetRunnable(page, 45_000);
  if (!runnable) {
    issues.push("Dataset select empty after wait");
    return { flow: segment.toUpperCase(), path: flowPath, steps, outcome: "BLOCKED_NO_DATASET", pass: false, issues };
  }

  const ds = await datasetLabel(page);
  steps.push(`Dataset selected: ${ds}`);

  if (configure) {
    steps.push("Apply flow-specific configuration");
    await configure(page);
  } else if (segment === "llm") {
    await ensureFirstLlmModelSelectedForRun(page);
  }

  if (segment === "embedding" || segment === "rag") {
    steps.push("Wait for evaluation corpus index readiness");
    await expect(page.getByTestId("lab-evaluation-corpus-panel")).toBeVisible({ timeout: 20_000 });
    await expect
      .poll(async () => page.getByTestId(runTestId).isEnabled(), {
        timeout: 180_000,
        intervals: [1000, 2500, 5000],
      })
      .toBe(true);
  }

  await screenshot(page, `${segment}-02-configured.png`);

  const runButton = page.getByTestId(runTestId);
  steps.push(`Click ${runTestId}`);
  await expect(runButton).toBeEnabled({ timeout: 15_000 });
  await runButton.click();

  steps.push("Wait for terminal lab outcome");
  const outcome = await pollLabTerminalOutcome(page, segment === "llm" ? 480_000 : 360_000);
  steps.push(`Terminal outcome: ${outcome}`);

  const errorAlert = page.locator('[data-slot="card"]').getByRole("alert").first();
  const hasError = await errorAlert.isVisible().catch(() => false);
  if (hasError) {
    const errText = (await errorAlert.textContent()) ?? "";
    issues.push(`Visible error alert: ${errText.slice(0, 300)}`);
  }

  await screenshot(page, `${segment}-04-terminal.png`);

  let artifactPath: string | undefined;
  const resultsPanel = page.getByTestId("lab-benchmark-results-panel");
  if (await resultsPanel.isVisible().catch(() => false)) {
    const tableText = (await resultsPanel.textContent())?.slice(0, 4000) ?? "";
    artifactPath = `${segment}_results_panel.txt`;
    writeEvidence(artifactPath, tableText);
    steps.push("Captured results panel text");
  }

  const rawSummary = page.locator("summary").filter({ hasText: /Raw async payload|JSON.*advanced/i });
  if (await rawSummary.isVisible().catch(() => false)) {
    await rawSummary.click();
    const pre = page.locator('[data-slot="card"] pre').filter({ hasText: /^\s*[\[{]/ }).first();
    const raw = (await pre.textContent()) ?? "";
    writeEvidence(`${segment}_raw_payload.json`, raw);
    artifactPath = `${segment}_raw_payload.json`;
    steps.push("Expanded raw async JSON payload");
  }

  const pass = !hasError && ["results", "comparison", "job_done"].includes(outcome);
  if (!pass && outcome === "error") {
    issues.push(`Terminal outcome error`);
  }
  if (!pass && outcome === "job_running") {
    issues.push("Job still running after timeout");
  }

  return {
    flow: segment.toUpperCase(),
    path: flowPath,
    steps,
    datasetLabel: ds,
    outcome,
    pass,
    issues,
    screenshot: `screenshots/${segment}-04-terminal.png`,
    artifactPath,
  };
}

test.describe.serial("UI evaluation validation @closure @fullstack @ui-evaluation", () => {
  test.beforeAll(() => {
    fs.mkdirSync(path.join(EVIDENCE_DIR, "screenshots"), { recursive: true });
  });

  test.beforeEach(async ({ page }) => {
    test.setTimeout(360_000);
    await installDockerWebappApiProxy(page);
    await prepareLabE2eTest(page);
  });

  test("LLM evaluation flow", async ({ page }) => {
    const ev = await runBenchmarkFlow(page, "llm", "lab-llm-run");
    writeEvidence("llm_flow_evidence.json", ev);
    expect(ev.pass, JSON.stringify(ev, null, 2)).toBe(true);
  });

  test("Embedding evaluation flow", async ({ page }) => {
    await ensureLabEvaluationCorpusReadyViaApi(page, "EMBEDDING_RETRIEVAL");
    const embeddingIds = await fetchSelectableEmbeddingModelIds(page);
    expect(embeddingIds.length).toBeGreaterThan(0);
    const ev = await runBenchmarkFlow(page, "embedding", "lab-embedding-run", async (p) => {
      await selectEmbeddingModelsByIds(p, [embeddingIds[0]]);
    });
    ev.modelOrEmbedding = embeddingIds[0];
    writeEvidence("embedding_flow_evidence.json", ev);
    expect(ev.pass, JSON.stringify(ev, null, 2)).toBe(true);
  });

  test("RAG evaluation flow (core subset)", async ({ page }) => {
    await ensureLabEvaluationCorpusReadyViaApi(page, "RAG_PRESET_END_TO_END");
    const ev = await runBenchmarkFlow(page, "rag", "lab-rag-run", async (p) => {
      await expect(p.getByTestId("lab-experimental-presets-list")).toBeVisible({ timeout: 20_000 });
      await p.getByTestId("lab-experimental-presets-select-core").click();
      await expect
        .poll(async () => (await p.getByTestId("lab-rag-run").isEnabled()) || (await p.locator('[data-testid^="lab-experimental-preset-P"]').first().isChecked().catch(() => false)), {
          timeout: 30_000,
          intervals: [500, 1500],
        })
        .toBe(true);
    });
    writeEvidence("rag_flow_evidence.json", ev);
    expect(ev.pass, JSON.stringify(ev, null, 2)).toBe(true);
  });

  test("Product preset catalog Demo_Best / Demo_NaiveFullCorpus / Demo_Worst", async ({ page }) => {
    await loginAsSeedUser(page);
    const headers = await authHeadersFromPage(page);
    const catalogRes = await page.request.get(productApiUrl("/chat/presets/catalog"), { headers });
    expect(catalogRes.ok()).toBeTruthy();
    const catalog = (await catalogRes.json()) as {
      productPresets: Array<{ id: string; name: string }>;
    };

    const required = ["Demo_Best", "Demo_NaiveFullCorpus", "Demo_Worst"] as const;
    const matrix = required.map((name) => {
      const p = catalog.productPresets?.find((x) => x.name === name);
      return { name, id: p?.id ?? null, inCatalog: Boolean(p) };
    });
    writeEvidence("preset_api_catalog.json", { matrix, productPresets: catalog.productPresets });

    await page.goto("/en/projects", { waitUntil: "domcontentloaded" });
    const projectName = uniqueProjectName("phase8-presets");
    await createAndActivateProject(page, projectName);
    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/, { timeout: 20_000 });
    await createNewChatConversation(page);
    await expect(page).toHaveURL(/[?&]conversationId=/, { timeout: 20_000 });

    const panel = await openChatConfigurationPanel(page);
    const presetSelect = panel.getByTestId("chat-preset-select");
    await expect(presetSelect).toBeVisible({ timeout: 15_000 });
    const uiOptions = await presetSelect.locator("option").evaluateAll((opts) =>
      opts.map((o) => ({
        value: (o as HTMLOptionElement).value,
        text: (o.textContent ?? "").trim(),
        disabled: (o as HTMLOptionElement).disabled,
      })),
    );

    const comparison = required.map((name) => {
      const api = matrix.find((m) => m.name === name);
      const ui = uiOptions.find((o) => new RegExp(name.replace(/_/g, "[ _]"), "i").test(o.text));
      return {
        name,
        apiId: api?.id,
        uiLabel: ui?.text,
        uiValue: ui?.value,
        selectable: Boolean(ui && !ui.disabled),
      };
    });

    for (const row of comparison) {
      if (row.uiValue && row.selectable) {
        await presetSelect.selectOption(row.uiValue);
        await expect(presetSelect).toHaveValue(row.uiValue);
      }
    }

    await screenshot(page, "presets-chat-selector.png");
    writeEvidence("preset_comparison.json", { comparison, uiOptions });

    for (const row of comparison) {
      expect(row.apiId, `${row.name} in API catalog`).toBeTruthy();
      expect(row.selectable, `${row.name} selectable in UI`).toBe(true);
    }
  });

  test("Error handling: run blocked without dataset", async ({ page }) => {
    await gotoLabEvaluationPage(page, "llm");
    const warn = page.getByTestId("lab-benchmark-needs-dataset-warn");
    const runButton = page.getByTestId("lab-llm-run");
    const steps = ["Open LLM evaluation page"];

    if (await warn.isVisible().catch(() => false)) {
      steps.push("needs-dataset warning visible - run correctly blocked");
      await expect(runButton).toBeDisabled();
      await screenshot(page, "error-needs-dataset.png");
      writeEvidence("error_handling_evidence.json", {
        scenario: "no_dataset",
        pass: true,
        steps,
        runDisabled: await runButton.isDisabled(),
      });
      return;
    }

    await page.getByTestId("lab-benchmark-dataset-select").selectOption({ index: 0 });
    steps.push("Dataset present - clear selection to probe validation");
    await page.getByTestId("lab-benchmark-dataset-select").selectOption("");
    const disabled = await runButton.isDisabled();
    steps.push(`Run button disabled after clear: ${disabled}`);
    await screenshot(page, "error-empty-dataset.png");
    writeEvidence("error_handling_evidence.json", {
      scenario: "cleared_dataset",
      pass: disabled,
      steps,
      runDisabled: disabled,
    });
    expect(disabled).toBe(true);
  });
});
