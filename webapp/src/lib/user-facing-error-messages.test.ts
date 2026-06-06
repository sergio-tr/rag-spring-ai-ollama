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
});
