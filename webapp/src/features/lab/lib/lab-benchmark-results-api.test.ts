import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiDownloadBlob, apiFetch } from "@/lib/api-client";
import {
  downloadCampaignExport,
  downloadCampaignMvpItemsJson,
  downloadMvpExport,
  fetchCampaignComparison,
  fetchLabCampaignRuns,
  fetchLabEvaluationRun,
  fetchMvpItemsBundle,
  fetchMvpRollupsJson,
} from "@/features/lab/lib/lab-benchmark-results-api";
import * as experimentalApi from "@/features/lab/lib/experimental-datasets-api";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiDownloadBlob: vi.fn(),
  };
});

vi.spyOn(experimentalApi, "triggerBrowserBlobDownload").mockImplementation(() => {});

describe("lab-benchmark-results-api", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    vi.mocked(apiDownloadBlob).mockReset();
  });

  afterEach(() => {
    vi.mocked(experimentalApi.triggerBrowserBlobDownload).mockClear();
  });

  it("fetchLabEvaluationRun hits encoded run path", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ id: "run-1" });
    await fetchLabEvaluationRun("abc/xyz");
    expect(apiFetch).toHaveBeenCalledWith(expect.stringContaining(encodeURIComponent("abc/xyz")));
  });

  it("fetchLabCampaignRuns and fetchCampaignComparison hit encoded campaign paths", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([{ id: "run-1" }]);
    vi.mocked(apiFetch).mockResolvedValueOnce({ winner: "P1" });

    await fetchLabCampaignRuns("campaign/with spaces");
    await fetchCampaignComparison("campaign/with spaces");

    const encoded = encodeURIComponent("campaign/with spaces");
    expect(vi.mocked(apiFetch).mock.calls[0][0]).toContain(`/lab/campaigns/${encoded}/runs`);
    expect(vi.mocked(apiFetch).mock.calls[1][0]).toContain(`/lab/campaigns/${encoded}/comparison`);
  });

  it("fetchMvpRollupsJson and fetchMvpItemsBundle use export paths", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ rollups: true });
    vi.mocked(apiFetch).mockResolvedValueOnce({ items: [] });
    await fetchMvpRollupsJson("r1");
    await fetchMvpItemsBundle("r1");
    expect(vi.mocked(apiFetch).mock.calls[0][0]).toContain("/export/mvp/rollups.json");
    expect(vi.mocked(apiFetch).mock.calls[1][0]).toContain("/export/mvp/items.json");
  });

  it("downloadMvpExport downloads csv with csv-specific filename", async () => {
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["a,b"]));
    await downloadMvpExport("rid", "items.csv");
    expect(apiDownloadBlob).toHaveBeenCalledWith(expect.stringContaining("/export/mvp/items.csv"));
    expect(experimentalApi.triggerBrowserBlobDownload).toHaveBeenCalledWith(
      expect.any(Blob),
      "lab-run-rid-mvp-items.csv",
    );
  });

  it("downloadCampaignMvpItemsJson delegates to campaign-items.json export", async () => {
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["{}"]));

    await downloadCampaignMvpItemsJson("campaign-1");

    expect(apiDownloadBlob).toHaveBeenCalledWith(
      expect.stringContaining("/lab/campaigns/campaign-1/export/campaign-items.json"),
    );
    expect(experimentalApi.triggerBrowserBlobDownload).toHaveBeenCalledWith(
      expect.any(Blob),
      "lab-campaign-campaign-1-campaign-items.json",
    );
  });

  it("downloadCampaignExport downloads summary and bundle exports by selected kind", async () => {
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["Campaign ID,Compared item"]));
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["bundle"]));

    await downloadCampaignExport("campaign-2", "summary.csv");
    await downloadCampaignExport("campaign-2", "bundle.json");

    expect(vi.mocked(apiDownloadBlob).mock.calls[0][0]).toContain("/export/summary.csv");
    expect(experimentalApi.triggerBrowserBlobDownload).toHaveBeenNthCalledWith(
      1,
      expect.any(Blob),
      "lab-campaign-campaign-2-summary.csv",
    );
    expect(vi.mocked(apiDownloadBlob).mock.calls[1][0]).toContain("/export/bundle.json");
    expect(experimentalApi.triggerBrowserBlobDownload).toHaveBeenNthCalledWith(
      2,
      expect.any(Blob),
      "lab-campaign-campaign-2-bundle.json",
    );
  });

  it("downloadCampaignExport downloads human-readable items csv", async () => {
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["Campaign ID,Run ID,LLM model"]));

    await downloadCampaignExport("campaign-3", "items.csv");

    expect(apiDownloadBlob).toHaveBeenCalledWith(expect.stringContaining("/export/items.csv"));
    expect(experimentalApi.triggerBrowserBlobDownload).toHaveBeenCalledWith(
      expect.any(Blob),
      "lab-campaign-campaign-3-items.csv",
    );
  });

  it("downloadMvpExport downloads json kinds with distinct filenames", async () => {
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["{}"]));
    await downloadMvpExport("rid", "items.json");
    expect(experimentalApi.triggerBrowserBlobDownload).toHaveBeenCalledWith(
      expect.any(Blob),
      "lab-run-rid-mvp-items.json",
    );
    vi.mocked(apiDownloadBlob).mockResolvedValueOnce(new Blob(["{}"]));
    await downloadMvpExport("rid", "rollups.json");
    expect(experimentalApi.triggerBrowserBlobDownload).toHaveBeenCalledWith(
      expect.any(Blob),
      "lab-run-rid-mvp-rollups.json",
    );
  });
});
