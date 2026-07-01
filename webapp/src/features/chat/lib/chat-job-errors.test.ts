import { describe, expect, it } from "vitest";

import {
  isKnownChatJobFailureCode,
  resolveChatJobFailureCode,
  resolveChatJobFailureUserHint,
} from "./chat-job-errors";
import type { AsyncTaskStatusDto } from "@/types/api";

function task(partial: Partial<AsyncTaskStatusDto> & Pick<AsyncTaskStatusDto, "status" | "terminal">): AsyncTaskStatusDto {
  return {
    id: "j1",
    taskType: "CHAT_MESSAGE",
    progressText: null,
    result: null,
    errorMessage: null,
    createdAt: "",
    updatedAt: "",
    startedAt: null,
    completedAt: null,
    failureCode: null,
    ...partial,
  };
}

describe("chat-job-errors", () => {
  it("maps known failure codes to translation keys via resolveChatJobFailureUserHint", () => {
    const hint = resolveChatJobFailureUserHint({
      task: task({
        status: "FAILED",
        terminal: true,
        failureCode: "CHAT_DOCUMENT_SCOPE_EMPTY",
        errorMessage: "ignored when code known",
      }),
      errorMessageSanitized: "",
      t: (key) => `TR:${key}`,
    });
    expect(hint).toBe("TR:chatJobFailure_CHAT_DOCUMENT_SCOPE_EMPTY");
  });

  it("falls back to sanitized errorMessage when code unknown", () => {
    const hint = resolveChatJobFailureUserHint({
      task: task({ status: "FAILED", terminal: true, failureCode: "CUSTOM_CODE" }),
      errorMessageSanitized: "Safe backend text",
      t: (key) => `TR:${key}`,
    });
    expect(hint).toBe("Safe backend text");
  });

  it("uses OpenAI-compatible LLM unavailable copy without mentioning Ollama", () => {
    const hint = resolveChatJobFailureUserHint({
      task: task({ status: "FAILED", terminal: true, failureCode: "LLM_UNAVAILABLE" }),
      errorMessageSanitized: "",
      t: (key) => `TR:${key}`,
      provider: "OPENAI_COMPATIBLE",
    });
    expect(hint).toBe("TR:chatJobFailure_LLM_UNAVAILABLE_OPENAI");
    expect(hint).not.toMatch(/ollama/i);
  });

  it("reads failureCode from result when top-level field absent", () => {
    const code = resolveChatJobFailureCode(
      task({
        status: "FAILED",
        terminal: true,
        failureCode: null,
        result: { failureCode: "LLM_UNAVAILABLE", phase: "failed" },
      }),
    );
    expect(code).toBe("LLM_UNAVAILABLE");
  });

  it("isKnownChatJobFailureCode narrows enum-like codes", () => {
    expect(isKnownChatJobFailureCode("LLM_UNAVAILABLE")).toBe(true);
    expect(isKnownChatJobFailureCode("CUSTOM")).toBe(false);
  });
});
