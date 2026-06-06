import { describe, expect, it } from "vitest";
import {
  extractTechnicalErrorCode,
  isTechnicalErrorMessage,
  mapUserFacingErrorMessage,
  mapUserFacingErrorMessageEnglish,
} from "./user-facing-error-messages";

const t = (key: string) => `i18n:${key}`;

describe("user-facing-error-messages", () => {
  it("extracts leading technical codes", () => {
    expect(extractTechnicalErrorCode("NO_READY_DOCUMENTS")).toBe("NO_READY_DOCUMENTS");
    expect(extractTechnicalErrorCode("FAILED_STALE_INGESTION: stuck")).toBe("FAILED_STALE_INGESTION");
    expect(extractTechnicalErrorCode("EMBEDDING_DIMENSION_MISMATCH: model x")).toBe(
      "EMBEDDING_DIMENSION_MISMATCH",
    );
  });

  it("detects technical-only messages", () => {
    expect(isTechnicalErrorMessage("BLOCKED_BY_MODEL_AVAILABILITY")).toBe(true);
    expect(isTechnicalErrorMessage("Something went wrong")).toBe(false);
  });

  it("mapUserFacingErrorMessage uses Lab i18n keys", () => {
    expect(mapUserFacingErrorMessage("NO_READY_DOCUMENTS", t, "fb")).toBe(
      "i18n:userError_NO_READY_DOCUMENTS",
    );
    expect(mapUserFacingErrorMessage("BLOCKED_BY_MODEL_AVAILABILITY", t, "fb")).toBe(
      "i18n:userError_BLOCKED_BY_MODEL_AVAILABILITY",
    );
    expect(mapUserFacingErrorMessage("EMBEDDING_DIMENSION_MISMATCH: x", t, "fb")).toBe(
      "i18n:userError_EMBEDDING_DIMENSION_MISMATCH",
    );
    expect(mapUserFacingErrorMessage("FEATURE_REQUIRES_INDEX", t, "fb")).toBe(
      "i18n:labConfigRequiresIndex",
    );
  });

  it("mapUserFacingErrorMessage hides unknown technical codes", () => {
    expect(mapUserFacingErrorMessage("SOME_UNKNOWN_CODE", t, "fb")).toBe("fb");
  });

  it("mapUserFacingErrorMessageEnglish provides English copy", () => {
    expect(mapUserFacingErrorMessageEnglish("NO_READY_DOCUMENTS", "fb")).toContain("knowledge base");
    expect(mapUserFacingErrorMessageEnglish("BLOCKED_BY_MODEL_AVAILABILITY", "fb")).toContain("two");
  });

  it("extractTechnicalErrorCode handles empty and embedded hints", () => {
    expect(extractTechnicalErrorCode(null)).toBeNull();
    expect(extractTechnicalErrorCode("   ")).toBeNull();
    expect(extractTechnicalErrorCode("plain error")).toBeNull();
    expect(extractTechnicalErrorCode("upstream FAILED_STALE_INGESTION watchdog")).toBe(
      "FAILED_STALE_INGESTION",
    );
    expect(extractTechnicalErrorCode("wrapper EXPERIMENTAL_DATASET_INVALID detail")).toBe(
      "EXPERIMENTAL_DATASET_INVALID",
    );
  });

  it("isTechnicalErrorMessage accepts code prefixes", () => {
    expect(isTechnicalErrorMessage("FAILED_STALE_INGESTION: timeout")).toBe(true);
    expect(isTechnicalErrorMessage("FAILED_STALE_INGESTION timeout")).toBe(true);
    expect(isTechnicalErrorMessage("")).toBe(false);
  });

  it("mapUserFacingErrorMessage falls back for empty and unknown technical messages", () => {
    expect(mapUserFacingErrorMessage("", t, "fb")).toBe("fb");
    expect(mapUserFacingErrorMessage("  ", t, "fb")).toBe("fb");
    expect(mapUserFacingErrorMessage("SOME_UNKNOWN_CODE", t, "fb")).toBe("fb");
    expect(mapUserFacingErrorMessage("Human readable detail", t, "fb")).toBe("Human readable detail");
  });

  it("mapUserFacingErrorMessage uses generic userError key when i18n key is missing", () => {
    const echoT = (key: string) => key;
    expect(mapUserFacingErrorMessage("DATASET_INVALID", echoT, "fb")).toBe("fb");
    expect(mapUserFacingErrorMessage("DATASET_INVALID", t, "fb")).toBe("i18n:userError_DATASET_INVALID");
  });

  it("isTechnicalErrorMessage rejects embedded codes without a leading prefix", () => {
    expect(isTechnicalErrorMessage("upstream EMBEDDING_DIMENSION_MISMATCH detail")).toBe(false);
  });

  it("mapUserFacingErrorMessageEnglish handles empty, unknown, and plain messages", () => {
    expect(mapUserFacingErrorMessageEnglish("", "fb")).toBe("fb");
    expect(mapUserFacingErrorMessageEnglish("UNKNOWN_CODE", "fb")).toBe("fb");
    expect(mapUserFacingErrorMessageEnglish("Please retry later", "fb")).toBe("Please retry later");
    expect(mapUserFacingErrorMessageEnglish("FAILED_EMBEDDING", "fb")).toContain("Embedding");
  });
});
