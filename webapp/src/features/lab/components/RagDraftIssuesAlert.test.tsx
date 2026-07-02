import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { RagDraftIssuesAlert } from "@/features/lab/components/rag-draft-issues-alert";
import type { LabDraftIssue } from "@/features/lab/lib/lab-draft-issues";
import { computeLabDraftIssues } from "@/features/lab/lib/lab-draft-issues";
import { defaultLabEvaluationDraft } from "@/features/lab/lib/lab-evaluation-draft";

describe("RagDraftIssuesAlert", () => {
  it("renders nothing when the issue list is empty", () => {
    render(
      <IntlTestProvider locale="en">
        <RagDraftIssuesAlert issues={[]} />
      </IntlTestProvider>,
    );

    expect(screen.queryByTestId("lab-evaluation-draft-warnings")).not.toBeInTheDocument();
    expect(screen.queryByText(/Saved Lab draft issues/i)).not.toBeInTheDocument();
  });

  it("renders title and bullet list when issues exist", () => {
    const issues: LabDraftIssue[] = [
      {
        code: "MISSING_PRESET",
        severity: "error",
        messageKey: "labConfigNoPresets",
      },
    ];

    render(
      <IntlTestProvider locale="en">
        <RagDraftIssuesAlert issues={issues} />
      </IntlTestProvider>,
    );

    expect(screen.getByText("Saved Lab draft issues — fix before running")).toBeInTheDocument();
    expect(screen.getByTestId("lab-evaluation-draft-warnings-list")).toBeInTheDocument();
    expect(screen.getByTestId("lab-evaluation-draft-warnings-issue-MISSING_PRESET")).toBeInTheDocument();
  });
});

describe("computeLabDraftIssues", () => {
  it("includes a specific NO_DOCUMENTS cause", () => {
    const issues = computeLabDraftIssues({
      kind: "RAG_PRESET_END_TO_END",
      draft: defaultLabEvaluationDraft(),
      warnings: {
        datasetDeletedOrUnknown: false,
        datasetIncompatibleWithBenchmark: false,
        llmModelInvalid: false,
        llmModelsInvalid: [],
        embeddingModelInvalid: false,
        embeddingModelsInvalid: [],
        presetsUnknown: [],
      },
      invalidLabPresetSelections: [],
      needsEvaluationCorpus: true,
      corpusReadiness: {
        corpusId: "c1",
        indexProjectId: null,
        documentCount: 0,
        readyCount: 0,
        storageReadyCount: 0,
        processingCount: 0,
        failedCount: 0,
        primaryBlocker: "NO_DOCUMENTS",
        primaryBlockerMessage: null,
        activeSnapshotId: null,
        reindexRequired: false,
        snapshotBlocker: null,
        selectedSnapshotIds: [],
        runnable: false,
      },
      availableLlmModelIds: [],
    });

    expect(issues.some((issue) => issue.code === "NO_DOCUMENTS")).toBe(true);
    expect(issues.find((issue) => issue.code === "NO_DOCUMENTS")?.messageKey).toBe(
      "evalDraftIssueNoDocuments",
    );
  });
});
