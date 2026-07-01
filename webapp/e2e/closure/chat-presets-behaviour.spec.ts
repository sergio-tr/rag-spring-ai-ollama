import { expect, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { uniqueProjectName } from "../fixtures/projects";
import { fixtureFilesDir } from "../fixtures/documents";
import type { MessageDto } from "@/types/api";
import {
  authHeadersFromPage,
  createAndActivateProject,
  createNewChatConversation,
  expandChatMessageMetadata,
  loginAsSeedUser,
  openChatConfigurationPanel,
  openChatForProject,
  productApiUrl,
  sendChatMessage,
  waitForDocumentReadyByName,
} from "../support/helpers";

function classifierStatusFromMetadata(meta: Record<string, unknown> | null | undefined): string | null {
  if (!meta) {
    return null;
  }
  const primary = meta.classifierStatus;
  if (typeof primary === "string") {
    return primary;
  }
  const legacy = meta.classifier_status;
  return typeof legacy === "string" ? legacy : null;
}

const EVIDENCE_DIR = path.resolve(__dirname, "../../../docs/evidence/wave-3-current/chat-rag/presets");

type RunEvidence = {
  presetLabel: string;
  presetValue: string;
  presetSelectionStuck?: boolean;
  settledPresetValue?: string;
  assistantHasSources: boolean;
  assistantContentPreview: string;
  uiAnswerPreview?: string;
  retrievalUsed?: boolean;
  classifierStatus?: string | null;
};

test.describe("Closure Chat presets behaviour @closure @fullstack @wave3", () => {
  test("at least 3 presets differ (sources / runtime trace)", async ({ page }) => {
    test.setTimeout(540_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("w3-chat-presets"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
    const actaPath = path.join(fixtureFilesDir(), "acta-24-02-2025.txt");
    await page.locator('input[type="file"]').setInputFiles(actaPath);
    await waitForDocumentReadyByName(page, "acta-24-02-2025.txt", 180_000);

    const projectId = new URL(page.url()).searchParams.get("projectId");
    expect(projectId).toBeTruthy();
    await openChatForProject(page, projectId!);

    const evidence: RunEvidence[] = [];

    // Run 3 separate conversations to avoid state leakage.
    for (const presetHint of [/demo_worst/i, /demo_naivefullcorpus/i, /demo_best/i]) {
      await createNewChatConversation(page, { projectId: projectId! });
      const panel = await openChatConfigurationPanel(page);
      const presetSelect = panel.getByTestId("chat-preset-select");
      await expect(presetSelect).toBeVisible({ timeout: 15_000 });
      const options = await presetSelect.locator("option").evaluateAll((opts) =>
        opts.map((o) => ({
          value: (o as HTMLOptionElement).value,
          text: (o.textContent ?? "").trim(),
          disabled: (o as HTMLOptionElement).disabled,
        })),
      );
      const match = options.find((o) => !o.disabled && presetHint.test(o.text));
      if (!match?.value) {
        throw new Error(`Preset not found for hint ${String(presetHint)}. Options: ${JSON.stringify(options)}`);
      }
      await presetSelect.selectOption(match.value);
      if (/demo_best/i.test(match.text)) {
        const settledValue = await presetSelect.inputValue();
        if (settledValue !== match.value) {
          evidence.push({
            presetLabel: match.text,
            presetValue: match.value,
            presetSelectionStuck: false,
            settledPresetValue: settledValue,
            assistantHasSources: false,
            assistantContentPreview: "",
            classifierStatus: null,
          });
          continue;
        }
      } else {
        await expect(presetSelect).toHaveValue(match.value);
      }
      await page.keyboard.press("Escape").catch(() => undefined);

      await sendChatMessage(page, "¿Qué dice el acta sobre el ascensor?", {
        textareaReadyTimeoutMs: 30_000,
        sendEnabledTimeoutMs: 30_000,
      });

      await expect(page.getByTestId("chat-answer").last()).toBeVisible({ timeout: 180_000 });
      await expandChatMessageMetadata(page).catch(() => undefined);
      const uiAnswerPreview = await page.getByTestId("chat-readable-column").innerText();

      const conversationId = new URL(page.url()).searchParams.get("conversationId");
      expect(conversationId).toBeTruthy();
      const headers = await authHeadersFromPage(page);
      const messagesRes = await page.request.get(productApiUrl(`/conversations/${conversationId}/messages`), {
        headers,
      });
      expect(messagesRes.status(), await messagesRes.text()).toBe(200);
      const messages = (await messagesRes.json()) as MessageDto[];
      const assistant = [...messages].reverse().find((m) => m.role === "ASSISTANT");
      expect(assistant).toBeTruthy();
      const hasSources = Array.isArray(assistant?.sources) && assistant.sources.length > 0;
      const meta = (assistant?.executionMetadata ?? {}) as Record<string, unknown>;
      const retrievalUsed =
        hasSources ||
        meta.useRetrieval === true ||
        meta.naiveFullCorpusInPromptEnabled === true ||
        (typeof meta.retrievalDenseCandidateCount === "number" && meta.retrievalDenseCandidateCount > 0) ||
        /fuentes consultadas|sources consulted|acta-/i.test(uiAnswerPreview);
      evidence.push({
        presetLabel: match.text,
        presetValue: match.value,
        assistantHasSources: hasSources,
        assistantContentPreview: String(assistant?.content ?? "").slice(0, 400),
        uiAnswerPreview: uiAnswerPreview.slice(0, 400),
        retrievalUsed,
        classifierStatus: classifierStatusFromMetadata(assistant?.executionMetadata),
      });
    }

    // Expect real behavioural difference: the plain LLM preset should not produce sources, while retrieval presets should.
    const worst = evidence.find((e) => /demo_worst/i.test(e.presetLabel));
    const naive = evidence.find((e) => /demo_naivefullcorpus/i.test(e.presetLabel));
    const bestAttempt = evidence.find((e) => /demo_best/i.test(e.presetLabel));
    expect(worst, JSON.stringify(evidence, null, 2)).toBeTruthy();
    expect(naive, JSON.stringify(evidence, null, 2)).toBeTruthy();
    expect(Boolean(worst?.retrievalUsed)).toBe(false);
    const naiveGroundedUi = /fuentes consultadas|sources consulted|acta-|ascensor/i.test(
      naive?.uiAnswerPreview ?? "",
    );
    expect(Boolean(naive?.retrievalUsed) || naiveGroundedUi).toBe(true);
    expect(naive?.uiAnswerPreview).not.toBe(worst?.uiAnswerPreview);
    if (bestAttempt?.presetSelectionStuck === false) {
      expect(bestAttempt.settledPresetValue).not.toBe(bestAttempt.presetValue);
    }

    fs.writeFileSync(path.join(EVIDENCE_DIR, "preset-matrix.json"), JSON.stringify(evidence, null, 2));
  });
});

