import { describe, expect, it } from "vitest";
import {
  extractTechnicalErrorCode,
  isTechnicalErrorMessage,
  isZodLikeValidationMessage,
  mapUserFacingErrorMessage,
  mapUserFacingErrorMessageEnglish,
  resolveUserFacingErrorDisplay,
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
    expect(mapUserFacingErrorMessage("BLOCKED_BY_MODEL_AVAILABILITY", t, "fb", "OPENAI_COMPATIBLE")).toBe(
      "i18n:userError_BLOCKED_BY_MODEL_AVAILABILITY_OPENAI",
    );
    expect(mapUserFacingErrorMessage("EMBEDDING_DIMENSION_MISMATCH: x", t, "fb")).toBe(
      "i18n:userError_EMBEDDING_DIMENSION_MISMATCH",
    );
    expect(mapUserFacingErrorMessage("FEATURE_REQUIRES_INDEX", t, "fb")).toBe(
      "i18n:labConfigRequiresIndex",
    );
    expect(mapUserFacingErrorMessage("LAB_KB_LOAD_FAILED", t, "fb")).toBe("i18n:labKbLoadFailed");
  });

  it("mapUserFacingErrorMessage hides unknown technical codes", () => {
    expect(mapUserFacingErrorMessage("SOME_UNKNOWN_CODE", t, "fb")).toBe("fb");
  });

  it("mapUserFacingErrorMessageEnglish provides English copy", () => {
    expect(mapUserFacingErrorMessageEnglish("NO_READY_DOCUMENTS", "fb")).toContain("knowledge base");
    expect(mapUserFacingErrorMessageEnglish("BLOCKED_BY_MODEL_AVAILABILITY", "fb")).toContain("two");
    expect(mapUserFacingErrorMessageEnglish("BLOCKED_BY_MODEL_AVAILABILITY", "fb", "OPENAI_COMPATIBLE")).not.toMatch(
      /ollama|installed on the server/i,
    );
    expect(mapUserFacingErrorMessageEnglish("BLOCKED_BY_MODEL_AVAILABILITY", "fb", "OPENAI_COMPATIBLE")).toContain(
      "catalog",
    );
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
    expect(mapUserFacingErrorMessage("DATASET_INVALID", echoT, "fb")).toContain("dataset");
    expect(mapUserFacingErrorMessage("DATASET_INVALID", t, "fb")).toBe("i18n:userError_DATASET_INVALID");
  });

  it("isTechnicalErrorMessage rejects embedded codes without a leading prefix", () => {
    expect(isTechnicalErrorMessage("upstream EMBEDDING_DIMENSION_MISMATCH detail")).toBe(false);
  });

  it("isTechnicalErrorMessage treats JDBC and NPE messages as technical", () => {
    expect(
      isTechnicalErrorMessage(
        "PreparedStatementCallback; bad SQL grammar [SELECT COUNT(*) FROM vector_store]",
      ),
    ).toBe(true);
    expect(isTechnicalErrorMessage("NullPointerException")).toBe(true);
    expect(
      mapUserFacingErrorMessage(
        "PreparedStatementCallback; bad SQL grammar [SELECT COUNT(*)]",
        t,
        "fb",
      ),
    ).toBe("fb");
  });

  it("mapUserFacingErrorMessageEnglish handles empty, unknown, and plain messages", () => {
    expect(mapUserFacingErrorMessageEnglish("", "fb")).toBe("fb");
    expect(mapUserFacingErrorMessageEnglish("UNKNOWN_CODE", "fb")).toBe("fb");
    expect(mapUserFacingErrorMessageEnglish("Please retry later", "fb")).toBe("Please retry later");
    expect(mapUserFacingErrorMessageEnglish("FAILED_EMBEDDING", "fb")).toContain("Embedding");
  });

  it("mapUserFacingErrorMessage maps preset and multi-turn reason codes", () => {
    expect(mapUserFacingErrorMessage("FUTURE_MULTI_TURN_NOT_SELECTABLE", t, "fb")).toBe(
      "i18n:labConfigNotSingleTurn",
    );
    expect(mapUserFacingErrorMessage("REQUIRES_MULTI_TURN", t, "fb")).toBe("i18n:labConfigNotSingleTurn");
    expect(mapUserFacingErrorMessage("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED", t, "fb")).toBe(
      "i18n:labConfigP13",
    );
  });

  it("provider OpenAI-compatible error does not mention Ollama", () => {
    const display = resolveUserFacingErrorDisplay({
      raw: "The AI inference service is unavailable. Please try again once Ollama is running and reachable.",
      t,
      fallback: "fb",
      provider: "OPENAI_COMPATIBLE",
    });
    expect(display.primary).not.toMatch(/ollama/i);
    expect(display.primary).toBe("i18n:userError_INFERENCE_UNAVAILABLE_OPENAI");
  });

  it("validation error is human-readable", () => {
    const display = resolveUserFacingErrorDisplay({
      raw: "Too small: expected string to have >=1 characters",
      t,
      fallback: "fb",
    });
    expect(isZodLikeValidationMessage("Too small: expected string to have >=1 characters")).toBe(true);
    expect(display.primary).toBe("i18n:userError_VALIDATION_TOO_SHORT");
    expect(display.primary).not.toMatch(/Too small|>=1/);
    expect(display.technical).toContain("Too small");
  });

  it("technical code hidden under details payload", () => {
    const display = resolveUserFacingErrorDisplay({
      raw: "NO_READY_DOCUMENTS",
      t,
      fallback: "fb",
    });
    expect(display.primary).not.toBe("NO_READY_DOCUMENTS");
    expect(display.primary).toBe("i18n:userError_NO_READY_DOCUMENTS");
    expect(display.technical).toBe("NO_READY_DOCUMENTS");
  });

  it("Ollama provider uses Ollama-specific inference copy key", () => {
    const display = resolveUserFacingErrorDisplay({
      raw: "LLM_UNAVAILABLE",
      t,
      fallback: "fb",
      provider: "OLLAMA_NATIVE",
    });
    expect(display.primary).toBe("i18n:userError_INFERENCE_UNAVAILABLE_OLLAMA");
  });
});
