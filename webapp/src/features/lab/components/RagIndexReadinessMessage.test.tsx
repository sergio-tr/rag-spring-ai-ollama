import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { RagIndexReadinessMessage } from "@/features/lab/components/rag-index-readiness-message";
import { resolveRagIndexReadinessDisplay } from "@/features/lab/lib/rag-index-readiness";

const readinessBase = {
  corpusId: "c1",
  indexProjectId: "p1",
  documentCount: 2,
  readyCount: 2,
  storageReadyCount: 2,
  processingCount: 0,
  failedCount: 0,
  primaryBlocker: null,
  primaryBlockerMessage: null,
  activeSnapshotId: null,
  reindexRequired: true,
  snapshotBlocker: "REINDEX_REQUIRED",
  selectedSnapshotIds: [],
  runnable: false,
};

const summary = {
  id: "c1",
  name: "Lab corpus",
  sourceType: "UPLOAD",
  documentCount: 2,
  readyCount: 2,
  failedCount: 0,
  documents: [],
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

describe("resolveRagIndexReadinessDisplay", () => {
  it("shows info when docs exist and autoReindex is enabled", () => {
    const display = resolveRagIndexReadinessDisplay({
      selectedEmbeddingModelId: "bge-m3",
      baselineEmbeddingModelId: "bge-m3",
      autoReindex: true,
      reuseCompatibleActiveSnapshot: true,
      readiness: readinessBase,
      summary,
    });

    expect(display?.kind).toBe("info");
    expect(display?.messageKey).toBe("labEvalIndexWillPrepare");
    expect(display?.testId).toBe("lab-rag-index-will-prepare-info");
  });

  it("does not show the generic blocking warning when autoReindex is enabled after docs were added", () => {
    const display = resolveRagIndexReadinessDisplay({
      selectedEmbeddingModelId: "bge-m3",
      baselineEmbeddingModelId: "bge-m3",
      autoReindex: true,
      reuseCompatibleActiveSnapshot: true,
      readiness: readinessBase,
      summary,
    });

    expect(display?.messageKey).not.toBe("benchmarkRagEmbeddingIndexWarning");
    expect(display?.messageKey).not.toBe("benchmarkRagIndexBlockingWarning");
  });

  it("shows a contextual warning when the embedding model changed", () => {
    const display = resolveRagIndexReadinessDisplay({
      selectedEmbeddingModelId: "snowflake-arctic-embed2",
      baselineEmbeddingModelId: "bge-m3",
      autoReindex: true,
      reuseCompatibleActiveSnapshot: true,
      readiness: readinessBase,
      summary,
    });

    expect(display?.kind).toBe("warning");
    expect(display?.messageKey).toBe("benchmarkRagEmbeddingChangedWarning");
  });

  it("shows blocking warning when autoReindex is disabled and no snapshot exists", () => {
    const display = resolveRagIndexReadinessDisplay({
      selectedEmbeddingModelId: "bge-m3",
      baselineEmbeddingModelId: "bge-m3",
      autoReindex: false,
      reuseCompatibleActiveSnapshot: false,
      readiness: readinessBase,
      summary,
    });

    expect(display?.kind).toBe("blocking");
    expect(display?.messageKey).toBe("benchmarkRagIndexBlockingWarning");
  });
});

describe("RagIndexReadinessMessage", () => {
  it("renders the resolved readiness copy", () => {
    render(
      <IntlTestProvider locale="en">
        <RagIndexReadinessMessage
          display={{
            kind: "info",
            messageKey: "labEvalIndexWillPrepare",
            testId: "lab-rag-index-will-prepare-info",
          }}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByTestId("lab-rag-index-will-prepare-info")).toHaveTextContent(
      /prepare the required index before running/i,
    );
  });
});
