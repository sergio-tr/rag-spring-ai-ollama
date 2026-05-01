import { describe, it, expect, beforeEach } from "vitest";
import { useChatExplainStore } from "./chat-explain.store";

describe("useChatExplainStore", () => {
  beforeEach(() => {
    useChatExplainStore.setState({
      lastDone: null,
      streamingText: "",
      isStreaming: false,
    });
  });

  it("appends streaming chunks and resets", () => {
    useChatExplainStore.getState().appendStreaming("a");
    useChatExplainStore.getState().appendStreaming("b");
    expect(useChatExplainStore.getState().streamingText).toBe("ab");
    useChatExplainStore.getState().setStreamingText("full");
    expect(useChatExplainStore.getState().streamingText).toBe("full");
    useChatExplainStore.getState().resetStreaming();
    expect(useChatExplainStore.getState().streamingText).toBe("");
  });

  it("tracks lastDone and streaming flag", () => {
    const payload = {
      answer: "x",
      queryType: null,
      usedTool: false,
      toolUsed: null,
      sources: [],
      pipelineSteps: [],
    };
    useChatExplainStore.getState().setLastDone(payload);
    expect(useChatExplainStore.getState().lastDone).toEqual(payload);
    useChatExplainStore.getState().setStreaming(true);
    expect(useChatExplainStore.getState().isStreaming).toBe(true);
  });
});
