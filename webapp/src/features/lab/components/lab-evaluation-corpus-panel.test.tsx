import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LabEvaluationCorpusPanel } from "./lab-evaluation-corpus-panel";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string, values?: Record<string, number>) => {
    if (key === "labCorpusSelectedSummary" && values) {
      return `Selected corpus: ${values.total} documents (${values.ready} ready)`;
    }
    const map: Record<string, string> = {
      labCorpusTitle: "Evaluation documents",
      labCorpusHelp: "Attach documents for this evaluation.",
      labCorpusUploadLabel: "Upload",
      labCorpusAttachFromProject: "Use documents from project",
    };
    return map[key] ?? key;
  },
}));

const refresh = vi.fn();
const ensureCorpus = vi.fn().mockResolvedValue({ id: "corpus-1", documentCount: 0, readyCount: 0, documents: [] });
const uploadDocument = vi.fn().mockResolvedValue(undefined);
const attachFromProject = vi.fn().mockResolvedValue(undefined);

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn().mockResolvedValue([
    { id: "d1", corpusScope: "PROJECT_SHARED", fileName: "doc.pdf", status: "READY" },
  ]),
  apiProductPath: (path: string) => path,
}));

vi.mock("@/features/lab/hooks/use-evaluation-corpus", () => ({
  useEvaluationCorpus: () => ({
    summary: { id: "corpus-1", documentCount: 2, readyCount: 1, documents: [{ id: "d1", fileName: "a.pdf", status: "READY" }] },
    loading: false,
    error: null,
    refresh,
    ensureCorpus,
    uploadDocument,
    attachFromProject,
  }),
}));

describe("LabEvaluationCorpusPanel", () => {
  it("renders without requiring active project", () => {
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.getByTestId("lab-evaluation-corpus-panel")).toBeInTheDocument();
    expect(screen.getByTestId("lab-corpus-summary")).toHaveTextContent(/2 documents/i);
    expect(screen.getByTestId("lab-corpus-upload-input")).toBeInTheDocument();
  });

  it("shows attach-from-project only when optional project is set", async () => {
    const { rerender } = render(
      <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.queryByTestId("lab-corpus-attach-project")).not.toBeInTheDocument();

    rerender(
      <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={vi.fn()} optionalProjectId="p1" />,
    );
    expect(screen.getByTestId("lab-corpus-attach-project")).toBeInTheDocument();
    await userEvent.click(screen.getByTestId("lab-corpus-attach-project"));
    await waitFor(() => expect(attachFromProject).toHaveBeenCalled());
  });
});
