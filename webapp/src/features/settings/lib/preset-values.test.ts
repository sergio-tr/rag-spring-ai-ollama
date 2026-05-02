import { describe, expect, it } from "vitest";
import {
  buildPresetSaveValues,
  findKeysRejectedBySanitizer,
  partitionPresetImportValues,
} from "./preset-values";

describe("preset-values", () => {
  describe("findKeysRejectedBySanitizer", () => {
    it("lists keys outside the backend whitelist", () => {
      expect(findKeysRejectedBySanitizer({ topK: 5, hackerKey: true })).toEqual(["hackerKey"]);
    });

    it("returns empty when all keys are allowed", () => {
      expect(findKeysRejectedBySanitizer({ topK: 3 })).toEqual([]);
    });
  });

  describe("partitionPresetImportValues", () => {
    it("routes editable keys into structured map and keeps other allowed keys as extras", () => {
      const editableKeys = ["topK", "llmModel"];
      const { structured, extrasAllowed } = partitionPresetImportValues(
        { topK: 8, llmModel: "x", embeddingModel: "emb-v1" },
        editableKeys,
      );
      expect(structured).toEqual({ topK: 8, llmModel: "x" });
      expect(extrasAllowed).toEqual({ embeddingModel: "emb-v1" });
    });

    it("drops keys outside sanitizer list during partition", () => {
      const { structured, extrasAllowed } = partitionPresetImportValues({ topK: 2, unknown: 1 }, ["topK"]);
      expect(structured).toEqual({ topK: 2 });
      expect(extrasAllowed).toEqual({});
    });
  });

  describe("buildPresetSaveValues", () => {
    it("merges extras with structured values and drops unknown keys", () => {
      const editableKeys = ["topK"];
      const values = buildPresetSaveValues({ topK: 4 }, editableKeys, {
        embeddingModel: "e",
        illegal: "no",
      } as Record<string, unknown>);
      expect(values).toEqual({ topK: 4, embeddingModel: "e" });
      expect(values).not.toHaveProperty("illegal");
    });
  });
});
