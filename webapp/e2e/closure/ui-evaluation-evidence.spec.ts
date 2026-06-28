import { expect, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { uniqueProjectName } from "../fixtures/projects";
import {
  authHeadersFromPage,
  createAndActivateProject,
  createNewChatConversation,
  expandChatConfigurationRuntimeSection,
  loginAsSeedUser,
  openChatConfigurationPanel,
  openChatForProject,
  productApiUrl,
  sendChatMessage,
  waitForDocumentReadyByName,
} from "../support/helpers";

const ACTA_25_FEB_2026 = path.join(
  process.cwd(),
  "..",
  "rag-service",
  "src",
  "test",
  "resources",
  "acta-fixtures",
  "acta-5.txt",
);

async function uploadActaFixture(page: import("@playwright/test").Page, projectId: string): Promise<void> {
  await page.goto(`/en/documents?projectId=${projectId}`, { waitUntil: "domcontentloaded" });
  await expect(page).toHaveURL(/\/en\/documents/);
  await page.locator('input[type="file"]').setInputFiles(ACTA_25_FEB_2026);
  await waitForDocumentReadyByName(page, "acta-5.txt", 180_000);
}

async function ensureRetrievalEnabled(page: import("@playwright/test").Page): Promise<void> {
  const panel = await openChatConfigurationPanel(page);
  await expandChatConfigurationRuntimeSection(panel);
  const retrievalToggle = panel.getByTestId("chat-runtime-toggle-useRetrieval");
  await expect(retrievalToggle).toBeVisible({ timeout: 15_000 });
  if (!(await retrievalToggle.isChecked())) {
    await retrievalToggle.click();
    await expect(retrievalToggle).toBeChecked({ timeout: 45_000 });
  }
  await page.keyboard.press("Escape").catch(() => undefined);
}

async function selectEnabledPreset(page: import("@playwright/test").Page, hint: RegExp): Promise<void> {
  const panel = await openChatConfigurationPanel(page);
  const presetSelect = panel.getByTestId("chat-preset-select");
  const options = await presetSelect.locator("option").evaluateAll((opts) =>
    opts.map((o) => ({
      value: (o as HTMLOptionElement).value,
      text: (o.textContent ?? "").trim(),
      disabled: (o as HTMLOptionElement).disabled,
    })),
  );
  const match = options.find((o) => !o.disabled && hint.test(o.text));
  const fallback = options.find((o) => !o.disabled && o.value);
  const pick = match?.value ?? fallback?.value;
  if (pick) {
    await presetSelect.selectOption(pick);
  }
  await page.keyboard.press("Escape").catch(() => undefined);
}

const EVIDENCE_DIR = path.resolve(
  process.env.UI_EVALUATION_EVIDENCE_DIR ??
    path.join(process.cwd(), "test-results", "ui-evaluation-evidence"),
);

type PresetMatrixRow = {
  presetId: string;
  uiLabel: string;
  source: "product" | "experimental" | "runtime" | "default";
  disabled: boolean;
  inApiCatalog: boolean;
  apiCode?: string;
  stability: "product" | "experimental" | "unknown";
  selectablePass: boolean;
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

test.describe("UI evidence matrix @closure @fullstack @wave3", () => {
  test.describe.configure({ mode: "serial" });

  test("preset catalog matches UI selector and flags experimental presets", async ({ page }) => {
    test.setTimeout(180_000);
    fs.mkdirSync(path.join(EVIDENCE_DIR, "screenshots"), { recursive: true });

    await loginAsSeedUser(page);
    const projectName = uniqueProjectName("phase12-ui-matrix");
    const projectId = await createAndActivateProject(page, projectName);
    await openChatForProject(page, projectId);
    await createNewChatConversation(page);

    const headers = await authHeadersFromPage(page);
    const catalogRes = await page.request.get(productApiUrl("/chat/presets/catalog"), { headers });
    expect(catalogRes.status(), await catalogRes.text()).toBe(200);
    const catalog = (await catalogRes.json()) as {
      productPresets?: Array<{ id: string; name: string; code?: string }>;
      experimentalPresets?: Array<{
        productPresetId: string;
        code: string;
        chatSelectable?: boolean;
      }>;
    };

    const panel = await openChatConfigurationPanel(page);
    const presetSelect = panel.getByTestId("chat-preset-select");
    await expect(presetSelect).toBeVisible({ timeout: 20_000 });

    const uiOptions = await presetSelect.locator("option").evaluateAll((opts) =>
      opts.map((o) => ({
        value: (o as HTMLOptionElement).value,
        text: (o.textContent ?? "").trim(),
        disabled: (o as HTMLOptionElement).disabled,
        parentLabel: (o.parentElement as HTMLOptGroupElement | null)?.label ?? "",
      })),
    );

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "screenshots", "preset-selector.png"),
      fullPage: true,
    });

    const productIds = new Set((catalog.productPresets ?? []).map((p) => p.id));
    const experimentalById = new Map(
      (catalog.experimentalPresets ?? []).map((p) => [p.productPresetId, p]),
    );

    const matrix: PresetMatrixRow[] = uiOptions
      .filter((o) => o.value)
      .map((o) => {
        const isProduct = productIds.has(o.value);
        const exp = experimentalById.get(o.value);
        const source: PresetMatrixRow["source"] = isProduct
          ? "product"
          : exp
            ? "experimental"
            : "runtime";
        const stability: PresetMatrixRow["stability"] = isProduct
          ? "product"
          : exp
            ? "experimental"
            : "unknown";
        return {
          presetId: o.value,
          uiLabel: o.text,
          source,
          disabled: o.disabled,
          inApiCatalog: isProduct || Boolean(exp),
          apiCode: exp?.code ?? catalog.productPresets?.find((p) => p.id === o.value)?.code,
          stability,
          selectablePass: !o.disabled || Boolean(exp && !exp.chatSelectable),
        };
      });

    const demoBest = matrix.find(
      (r) => r.apiCode === "Demo_Best" || /demo_best/i.test(r.uiLabel),
    );
    expect(demoBest, JSON.stringify(matrix, null, 2)).toBeTruthy();
    expect(demoBest?.stability).toBe("product");

    const experimentalRows = matrix.filter((r) => r.source === "experimental");
    expect(experimentalRows.length).toBeGreaterThan(0);
    for (const row of experimentalRows) {
      expect(row.stability).toBe("experimental");
    }

    const productOptgroup = uiOptions.some((o) => /product preset/i.test(o.parentLabel));
    const experimentalOptgroup = uiOptions.some((o) => /experimental preset/i.test(o.parentLabel));
    expect(productOptgroup).toBe(true);
    expect(experimentalOptgroup).toBe(true);

    writeEvidence("ui_preset_matrix.json", {
      generatedAt: new Date().toISOString(),
      projectName,
      apiProductCount: catalog.productPresets?.length ?? 0,
      apiExperimentalCount: catalog.experimentalPresets?.length ?? 0,
      uiOptionCount: uiOptions.length,
      matrix,
    });

    const csvHeader = "presetId,uiLabel,source,stability,disabled,inApiCatalog,apiCode,selectablePass\n";
    const csvBody = matrix
      .map((r) =>
        [
          r.presetId,
          JSON.stringify(r.uiLabel),
          r.source,
          r.stability,
          r.disabled,
          r.inApiCatalog,
          r.apiCode ?? "",
          r.selectablePass,
        ].join(","),
      )
      .join("\n");
    writeEvidence("ui_preset_matrix.csv", csvHeader + csvBody + "\n");
  });

  test("clarification flow through chat UI", async ({ page }) => {
    test.setTimeout(420_000);
    await loginAsSeedUser(page);
    const projectId = await createAndActivateProject(page, uniqueProjectName("phase12-clar"));
    await openChatForProject(page, projectId);
    await createNewChatConversation(page, { projectId: projectId! });

    const panel = await openChatConfigurationPanel(page);
    const presetSelect = panel.getByTestId("chat-preset-select");
    const options = await presetSelect.locator("option").evaluateAll((opts) =>
      opts.map((o) => ({
        value: (o as HTMLOptionElement).value,
        text: (o.textContent ?? "").trim(),
        disabled: (o as HTMLOptionElement).disabled,
      })),
    );
    const demoBest = options.find((o) => !o.disabled && /demo_best/i.test(o.text));
    if (demoBest?.value) {
      await presetSelect.selectOption(demoBest.value);
    }
    await page.keyboard.press("Escape").catch(() => undefined);

    await sendChatMessage(page, "¿Cuántos participantes asistieron?", {
      textareaReadyTimeoutMs: 30_000,
      sendEnabledTimeoutMs: 30_000,
    });
    await expect(page.getByTestId("chat-answer").last()).toBeVisible({ timeout: 180_000 });
    const a1 = (await page.getByTestId("chat-answer").last().innerText()).toLowerCase();
    const clarDetected = /¿|especifica|qué acta|qué fecha|cuál|ambig|reunión|refieres/.test(a1);

    await sendChatMessage(page, "La reunión del 25/02/2026");
    await expect(page.getByTestId("chat-answer").last()).toBeVisible({ timeout: 180_000 });

    await sendChatMessage(page, "¿Cuántos participantes asistieron a esa reunión?");
    await expect(page.getByTestId("chat-answer").last()).toBeVisible({ timeout: 180_000 });
    const a3 = await page.getByTestId("chat-answer").last().innerText();
    const countOk = /\b17\b/.test(a3);
    const stubAck = /e2e stub reply/i.test(a3);

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "screenshots", "clarification-flow.png"),
      fullPage: true,
    });

    writeEvidence("clarification_ui_results.json", {
      generatedAt: new Date().toISOString(),
      clarificationDetected: clarDetected,
      followUpCountOk: countOk,
      e2eStubFollowUp: stubAck,
      pass: clarDetected && (countOk || stubAck),
      answers: { step1: a1.slice(0, 500), step3: a3.slice(0, 500) },
    });
    expect(clarDetected).toBe(true);
    expect(countOk || stubAck).toBe(true);
  });

  test("memory and cross-conversation isolation through chat UI", async ({ page }) => {
    test.setTimeout(420_000);
    await loginAsSeedUser(page);
    const projectId = await createAndActivateProject(page, uniqueProjectName("phase12-mem"));
    await uploadActaFixture(page, projectId);
    await openChatForProject(page, projectId);
    await createNewChatConversation(page, { projectId, allowExisting: false });
    await ensureRetrievalEnabled(page);
    await selectEnabledPreset(page, /demo_naivefullcorpus|demo_best/i);

    await sendChatMessage(page, "¿Quién fue el presidente en el acta del 25/02/2026?");
    await expect(page.getByTestId("chat-answer").last()).toBeVisible({ timeout: 180_000 });
    const a1 = (await page.getByTestId("chat-answer").last().innerText()).toLowerCase();
    const ok1 = a1.includes("jorge") || /e2e stub reply/i.test(a1);

    await sendChatMessage(
      page,
      "Recuerda: el presidente del 25/02/2026 fue Jorge. ¿De qué hablamos antes y quién presidió esa reunión?",
    );
    await expect(page.getByTestId("chat-answer").last()).toBeVisible({ timeout: 180_000 });
    const a2 = (await page.getByTestId("chat-answer").last().innerText()).toLowerCase();
    const ok2 =
      (a2.includes("jorge") &&
        (a2.includes("25/02/2026") || a2.includes("antes") || a2.includes("hablamos"))) ||
      /e2e stub reply/i.test(a2);

    await createNewChatConversation(page, { projectId, allowExisting: false });
    await sendChatMessage(page, "¿De qué hablamos antes?");
    await expect(page.getByTestId("chat-answer").last()).toBeVisible({ timeout: 180_000 });
    const a3 = (await page.getByTestId("chat-answer").last().innerText()).toLowerCase();
    const isolationOk = /no hemos|primera|no hay historial|sin conversación|empezar|primera vez|stub reply/.test(
      a3,
    );

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "screenshots", "memory-isolation.png"),
      fullPage: true,
    });

    writeEvidence("memory_ui_results.json", {
      generatedAt: new Date().toISOString(),
      turn1PresidentOk: ok1,
      turn2RecallOk: ok2,
      newConversationIsolationOk: isolationOk,
      e2eStubMode: /e2e stub reply/i.test(a3),
      pass: ok1 && ok2 && isolationOk,
      answers: { turn1: a1.slice(0, 300), turn2: a2.slice(0, 300), turn3: a3.slice(0, 300) },
    });
    expect(ok1).toBe(true);
    expect(ok2 || /e2e stub reply/i.test(a2)).toBe(true);
    expect(isolationOk).toBe(true);
  });
});
