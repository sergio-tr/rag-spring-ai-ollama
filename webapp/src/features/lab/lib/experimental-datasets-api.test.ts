import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiErrorMeta } from "@/lib/api-client";
import { ApiError, apiDownloadBlob, apiFetch } from "@/lib/api-client";
import {
  downloadExperimentalDatasetTemplate,
  EXPERIMENTAL_DATASET_TEMPLATE_KINDS,
  fetchExperimentalDatasetValidation,
  fetchExperimentalDatasets,
  parseExperimentalDatasetValidation422,
  suggestedTemplateFilename,
  triggerBrowserBlobDownload,
  uploadExperimentalDataset,
  uploadExperimentalDatasetAllow422,
} from "@/features/lab/lib/experimental-datasets-api";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiDownloadBlob: vi.fn(),
  };
});

function httpMeta(parsedJson: unknown): ApiErrorMeta {
  return { kind: "http", parsedJson };
}

describe("experimental-datasets-api", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    vi.mocked(apiDownloadBlob).mockReset();
  });

  it("lists all template kinds", () => {
    expect(EXPERIMENTAL_DATASET_TEMPLATE_KINDS).toContain("llm-model-baseline");
    expect(suggestedTemplateFilename("embedding-baseline")).toBe("embedding-baseline-template.xlsx");
  });

  it("downloadExperimentalDatasetTemplate calls apiDownloadBlob with encoded path", async () => {
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["x"]));
    const blob = await downloadExperimentalDatasetTemplate("rag-preset-benchmark");
    expect(apiDownloadBlob).toHaveBeenCalledWith(
      expect.stringContaining("/lab/dataset-templates/rag-preset-benchmark"),
    );
    expect(blob).toBeInstanceOf(Blob);
  });

  it("triggerBrowserBlobDownload creates a temporary anchor click", async () => {
    const click = vi.fn();
    const anchor = { href: "", download: "", rel: "", click } as unknown as HTMLAnchorElement;
    const createEl = vi.spyOn(document, "createElement").mockReturnValue(anchor);
    const objUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:mock");
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
    triggerBrowserBlobDownload(new Blob(["a"]), "f.xlsx");
    expect(createEl).toHaveBeenCalledWith("a");
    expect(anchor.download).toBe("f.xlsx");
    expect(click).toHaveBeenCalled();
    expect(objUrl).toHaveBeenCalled();
    await new Promise<void>((r) => queueMicrotask(r));
    expect(revoke).toHaveBeenCalledWith("blob:mock");
    createEl.mockRestore();
    objUrl.mockRestore();
    revoke.mockRestore();
  });

  it("fetchExperimentalDatasets delegates to apiFetch", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    await fetchExperimentalDatasets();
    expect(apiFetch).toHaveBeenCalledWith(expect.stringContaining("/lab/experimental-datasets"));
  });

  it("fetchExperimentalDatasetValidation encodes dataset id", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ issues: [], hasErrors: false, hasWarnings: false });
    await fetchExperimentalDatasetValidation("abc/def");
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringContaining(encodeURIComponent("abc/def")),
    );
  });

  describe("parseExperimentalDatasetValidation422", () => {
    it("returns null when meta or payload is unusable", () => {
      expect(parseExperimentalDatasetValidation422(undefined)).toBeNull();
      expect(parseExperimentalDatasetValidation422(httpMeta(null))).toBeNull();
      expect(parseExperimentalDatasetValidation422(httpMeta("x"))).toBeNull();
      expect(parseExperimentalDatasetValidation422(httpMeta({ error: "OTHER" }))).toBeNull();
      expect(parseExperimentalDatasetValidation422(httpMeta({ error: "EXPERIMENTAL_DATASET_INVALID" }))).toBeNull();
      expect(
        parseExperimentalDatasetValidation422(
          httpMeta({
            error: "EXPERIMENTAL_DATASET_INVALID",
            validationReport: null,
          }),
        ),
      ).toBeNull();
      expect(
        parseExperimentalDatasetValidation422(
          httpMeta({
            error: "EXPERIMENTAL_DATASET_INVALID",
            validationReport: { issues: "nope" },
          }),
        ),
      ).toBeNull();
    });

    it("parses a valid 422 experimental dataset invalid payload", () => {
      const parsed = parseExperimentalDatasetValidation422(
        httpMeta({
          error: "EXPERIMENTAL_DATASET_INVALID",
          validationReport: {
            issues: [{ code: "X", severity: "ERROR", sheet: "S", rowNumber: 1, column: "A", message: "m" }],
            hasErrors: true,
            hasWarnings: false,
          },
        }),
      );
      expect(parsed?.error).toBe("EXPERIMENTAL_DATASET_INVALID");
      expect(parsed?.validationReport.hasErrors).toBe(true);
      expect(parsed?.validationReport.issues).toHaveLength(1);
    });
  });

  it("uploadExperimentalDataset builds FormData with optional fields", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      datasetId: "id",
      experimentalDatasetType: "LLM_MODEL_BASELINE",
      persistedEvaluationDatasetType: "X",
      validationStatus: "VALID",
      questionCount: 1,
      rowCount: 1,
      validationReport: { issues: [], hasErrors: false, hasWarnings: false },
    });
    const file = new File([], "w.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await uploadExperimentalDataset({
      file,
      datasetType: "  llm-model-baseline  ",
      name: "  n  ",
      description: "  d  ",
    });
    const [, init] = vi.mocked(apiFetch).mock.calls[0];
    expect(init?.method).toBe("POST");
    const fd = init?.body as FormData;
    expect(fd.get("datasetType")).toBe("llm-model-baseline");
    expect(fd.get("name")).toBe("n");
    expect(fd.get("description")).toBe("d");
  });

  it("uploadExperimentalDataset skips empty trimmed name and description", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      datasetId: "id",
      experimentalDatasetType: "LLM_MODEL_BASELINE",
      persistedEvaluationDatasetType: "X",
      validationStatus: "VALID",
      questionCount: 1,
      rowCount: 1,
      validationReport: { issues: [], hasErrors: false, hasWarnings: false },
    });
    const file = new File([], "w.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await uploadExperimentalDataset({ file, datasetType: "llm-model-baseline", name: "   ", description: "\t" });
    const [, init] = vi.mocked(apiFetch).mock.calls[0];
    const fd = init?.body as FormData;
    expect(fd.get("name")).toBeNull();
    expect(fd.get("description")).toBeNull();
  });

  it("uploadExperimentalDatasetAllow422 returns ok payload on success", async () => {
    const payload = {
      datasetId: "id",
      experimentalDatasetType: "LLM_MODEL_BASELINE",
      persistedEvaluationDatasetType: "X",
      validationStatus: "VALID",
      questionCount: 1,
      rowCount: 1,
      validationReport: { issues: [], hasErrors: false, hasWarnings: false },
    };
    vi.mocked(apiFetch).mockResolvedValueOnce(payload);
    const file = new File([], "w.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const out = await uploadExperimentalDatasetAllow422({ file, datasetType: "llm-model-baseline" });
    expect(out.ok).toBe(true);
    if (out.ok) expect(out.data).toEqual(payload);
  });

  it("uploadExperimentalDatasetAllow422 maps 422 experimental invalid to failed outcome", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(422, "bad", {
        kind: "http",
        parsedJson: {
          error: "EXPERIMENTAL_DATASET_INVALID",
          validationReport: {
            issues: [],
            hasErrors: true,
            hasWarnings: false,
          },
        },
      }),
    );
    const file = new File([], "w.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const out = await uploadExperimentalDatasetAllow422({ file, datasetType: "llm-model-baseline" });
    expect(out.ok).toBe(false);
    if (!out.ok) expect(out.failed.error).toBe("EXPERIMENTAL_DATASET_INVALID");
  });

  it("uploadExperimentalDatasetAllow422 rethrows non-matching 422 errors", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(422, "bad", { kind: "http", parsedJson: { error: "OTHER" } }),
    );
    const file = new File([], "w.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await expect(uploadExperimentalDatasetAllow422({ file, datasetType: "llm-model-baseline" })).rejects.toBeInstanceOf(
      ApiError,
    );
  });

  it("uploadExperimentalDatasetAllow422 rethrows non-422 errors", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error("network"));
    const file = new File([], "w.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await expect(uploadExperimentalDatasetAllow422({ file, datasetType: "llm-model-baseline" })).rejects.toThrow(
      /network/,
    );
  });
});
