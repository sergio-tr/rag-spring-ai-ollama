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
  resumed: "Resumed",
  finishedAway: "Away",
  queued: "Queued",
  running: "Running",
  cancelling: "Cancelling",
  completed: "Done",
  completedWithFailures: "Done with failures",
  completedWithUnsupported: "Done with unsupported presets",
  noItemsExecuted: "No items executed",
  failed: "Failed",
  cancelled: "Cancelled",
  stoppedWaiting: "Reconnecting",
  unknownRunning: "Unknown",
  streamConfigurationError: "Live stream configuration error",
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

  it("JOBS-UI-002: reconnecting maps to human reconnect label", () => {
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "reconnecting" })).toBe("reconnecting");
    expect(getLabJobStatusLabel("reconnecting", labels)).toBe("Reconnecting");
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

  it("MODEL-DEFAULT-001: draft default embedding is empty until catalog fills it", () => {
    expect(defaultLabEvaluationDraft().embeddingModelId).toBe("");
    expect(LAB_DEFAULT_EMBEDDING_MODEL_ID).toBe("");
  });

  it("CORPUS-UI-001: corpus panel supports multiple file upload", () => {
    const src = readFileSync(
      join(root, "src/features/lab/components/lab-evaluation-corpus-panel.tsx"),
      "utf8",
    );
    expect(src).toMatch(/multiple/);
    expect(src).toMatch(/uploadDocuments/);
    expect(src).toMatch(/documents`/);
  });

  it("SSE-001: evaluation runner persists sse-only follow mode", () => {
    const src = readFileSync(
      join(root, "src/features/lab/components/lab-evaluation-run-card.tsx"),
      "utf8",
    );
    expect(src).toMatch(/useLabEvaluationModels/);
    expect(src).not.toMatch(/useModelsByType/);
    expect(src).not.toMatch(/embedding-campaign-preferred-models/);
    expect(src).toMatch(/followMode:\s*"sse"/);
    expect(src).not.toMatch(/followMode:\s*"poll"/);
    expect(src).not.toMatch(/Status poll:/i);
  });

  it("SSE-001: classifier panels use sse-only job follow", () => {
    const src = readFileSync(
      join(root, "src/app/[locale]/(app)/lab/classifier/lab-classifier-panels.tsx"),
      "utf8",
    );
    expect(src).toMatch(/mode:\s*"sse"/);
    expect(src).not.toMatch(/followModePoll/);
    expect(src).not.toMatch(/mode:\s*"poll"/);
  });

  it("CAMP-001: benchmark results panel exposes campaign comparison surface", () => {
    const src = readFileSync(
      join(root, "src/features/lab/components/lab-benchmark-results-panel.tsx"),
      "utf8",
    );
    expect(src).toMatch(/lab-campaign-comparison-panel/);
    expect(src).toMatch(/comparisonAxis/);
  });

  it("INDEX-001: backend indexing embedding guard properties are configured", () => {
    const props = readFileSync(
      join(root, "../rag-service/src/main/resources/application.properties"),
      "utf8",
    );
    expect(props).toMatch(/rag\.indexing\.embedding\.max-input-chars=/);
    expect(props).toMatch(/rag\.indexing\.embedding\.max-chunk-chars=/);
    expect(props).toMatch(/rag\.indexing\.embedding\.retry-on-context-length=/);
  });
});
