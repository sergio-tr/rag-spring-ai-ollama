import { describe, expect, it } from "vitest";
import { mapBenchmarkSkipReason } from "./lab-benchmark-skip-reasons";

const t = (key: string) => `i18n:${key}`;

describe("lab-benchmark-skip-reasons", () => {
  it("maps index preparation codes to product-safe copy", () => {
    const mapped = mapBenchmarkSkipReason("INDEX_PREPARATION_REQUIRED", t, "Skipped");
    expect(mapped.primary).toBe("i18n:benchmarkSkipIndexPreparationRequired");
    expect(mapped.technical).toBe("INDEX_PREPARATION_REQUIRED");
  });

  it("maps index preparation failure codes", () => {
    const mapped = mapBenchmarkSkipReason("SNAPSHOT_VECTOR_ROWS_MISSING", t, "Skipped");
    expect(mapped.primary).toBe("i18n:benchmarkSkipIndexPrepareFailed");
  });

  it("maps document incompatibility codes", () => {
    const mapped = mapBenchmarkSkipReason("NO_READY_DOCUMENTS", t, "Skipped");
    expect(mapped.primary).toBe("i18n:benchmarkSkipPresetCannotRunDocuments");
  });

  it("replaces legacy preparation prose with concise copy", () => {
    const mapped = mapBenchmarkSkipReason(
      "The system will prepare the required index before running.",
      t,
      "Skipped",
    );
    expect(mapped.primary).toBe("i18n:benchmarkSkipIndexPreparationRequired");
  });

  it("maps additional skip reason codes", () => {
    expect(mapBenchmarkSkipReason("REINDEX_REQUIRED", t, "Skipped").primary).toBe(
      "i18n:benchmarkSkipIndexPreparationRequired",
    );
    expect(mapBenchmarkSkipReason("REINDEX_FAILED", t, "Skipped").primary).toBe(
      "i18n:benchmarkSkipIndexPrepareFailed",
    );
    expect(mapBenchmarkSkipReason("MODEL_UNAVAILABLE", t, "Skipped").primary).toBe(
      "i18n:userError_MODEL_UNAVAILABLE",
    );
    expect(mapBenchmarkSkipReason("PRESET_NOT_SUPPORTED", t, "Skipped").primary).toBe(
      "i18n:labConfigUnsupportedPreset",
    );
  });

  it("falls back for unknown codes and long prose", () => {
    expect(mapBenchmarkSkipReason("UNKNOWN_SKIP_CODE", t, "Skipped").primary).toBe("Skipped");
    expect(mapBenchmarkSkipReason("Short note", t, "Skipped").primary).toBe("Short note");
    expect(mapBenchmarkSkipReason("x".repeat(150), t, "Skipped").primary).toBe("Skipped");
  });

  it("falls back when i18n returns the key unchanged", () => {
    const echo = (key: string) => key;
    expect(mapBenchmarkSkipReason("NO_ACTIVE_INDEX", echo, "Skipped").primary).toBe("Skipped");
    expect(mapBenchmarkSkipReason("will prepare the required index soon", echo, "Skipped").primary).toBe(
      "benchmarkSkipIndexPreparationRequired",
    );
  });
});
