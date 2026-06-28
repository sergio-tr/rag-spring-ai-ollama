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
});
