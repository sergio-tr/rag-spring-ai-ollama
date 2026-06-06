import { describe, expect, it } from "vitest";
import {
  embeddingComparisonAvailabilityStatus,
  filterCampaignCompatibleEmbeddingIds,
  isCampaignCompatibleEmbeddingModel,
  isEmbeddingDimensionCompatible,
} from "@/features/lab/lib/embedding-campaign-preferred-models";

describe("embedding-campaign-preferred-models", () => {
  it("excludes incompatible dimension tags from campaigns", () => {
    expect(isCampaignCompatibleEmbeddingModel("nomic-embed-text:latest")).toBe(false);
    expect(isCampaignCompatibleEmbeddingModel("qwen3-embedding:latest")).toBe(false);
    expect(isCampaignCompatibleEmbeddingModel("mxbai-embed-large:latest")).toBe(true);
  });

  it("filters selectable ids without requiring bge-m3", () => {
    expect(
      filterCampaignCompatibleEmbeddingIds([
        "mxbai-embed-large:latest",
        "nomic-embed-text:latest",
        "bge-m3:latest",
      ]),
    ).toEqual(["mxbai-embed-large:latest", "bge-m3:latest"]);
  });

  it("reports READY when two compatible models exist", () => {
    expect(embeddingComparisonAvailabilityStatus(2)).toBe("READY");
    expect(embeddingComparisonAvailabilityStatus(1)).toBe("BLOCKED_BY_MODEL_AVAILABILITY");
  });

  it("validates probed dimension against store width", () => {
    expect(isEmbeddingDimensionCompatible(1024)).toBe(true);
    expect(isEmbeddingDimensionCompatible(768)).toBe(false);
    expect(isEmbeddingDimensionCompatible(null)).toBe(false);
  });
});
