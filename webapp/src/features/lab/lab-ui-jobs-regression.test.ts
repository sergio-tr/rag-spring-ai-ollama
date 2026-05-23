import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  getLabJobUiPhase,
  getLabJobStatusLabel,
  type LabJobUiLabels,
} from "@/features/lab/lib/lab-task-ui";
import {
  defaultLabEvaluationDraft,
  LAB_DEFAULT_EMBEDDING_MODEL_ID,
} from "@/features/lab/lib/lab-evaluation-draft";

const root = join(dirname(fileURLToPath(import.meta.url)), "../../..");

const labels: LabJobUiLabels = {
  connecting: "Connecting",
  live: "Live",
  reconnecting: "Reconnecting",
  fallbackPolling: "Fallback",
  resumed: "Resumed",
  finishedAway: "Away",
  queued: "Queued",
  running: "Running",
  completed: "Done",
  failed: "Failed",
  cancelled: "Cancelled",
  stoppedWaiting: "Reconnecting",
  unknownRunning: "Unknown",
};

describe("LAB UI/Jobs regression (JOBS-UI, UX, MODEL, CORPUS)", () => {
  it("JOBS-UI-001: live phase wins over reconnecting connection state in UI mapping", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: { status: "RUNNING", terminal: false } as never,
        connectionState: "live",
      }),
    ).toBe("running");
    expect(getLabJobStatusLabel("live", labels)).toBe("Live");
  });

  it("JOBS-UI-002: fallback_polling maps to human fallback label", () => {
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "fallback_polling" })).toBe(
      "fallback_polling",
    );
    expect(getLabJobStatusLabel("fallback_polling", labels)).toBe("Fallback");
  });

  it("UX-001: Lab i18n avoids technical poll/stream labels in user strings", () => {
    const en = JSON.parse(readFileSync(join(root, "messages/en.json"), "utf8")) as {
      Lab: Record<string, string>;
    };
    const labStrings = Object.values(en.Lab).join("\n");
    expect(labStrings).not.toMatch(/Status poll:/i);
    expect(labStrings).not.toMatch(/Live stream:/i);
    expect(labStrings).not.toMatch(/canonical benchmark API/i);
  });

  it("MODEL-UI-001: model checkbox group component exists for Lab", () => {
    const src = readFileSync(
      join(root, "src/features/lab/components/model-checkbox-group.tsx"),
      "utf8",
    );
    expect(src).toMatch(/type="checkbox"/);
    expect(src).toMatch(/selectedIds/);
  });

  it("MODEL-DEFAULT-001: draft default embedding is mxbai-embed-large", () => {
    expect(defaultLabEvaluationDraft().embeddingModelId).toBe(LAB_DEFAULT_EMBEDDING_MODEL_ID);
    expect(LAB_DEFAULT_EMBEDDING_MODEL_ID).toBe("mxbai-embed-large");
  });

  it("CORPUS-UI-001: corpus panel supports multiple file upload", () => {
    const src = readFileSync(
      join(root, "src/features/lab/components/lab-evaluation-corpus-panel.tsx"),
      "utf8",
    );
    expect(src).toMatch(/multiple/);
    expect(src).toMatch(/uploadDocuments/);
  });
});
