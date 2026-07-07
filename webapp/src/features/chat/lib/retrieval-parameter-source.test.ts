import { describe, expect, it } from "vitest";
import { retrievalParameterSourceLabelKey } from "@/features/chat/lib/retrieval-parameter-source";

describe("retrieval parameter source labels", () => {
  it("maps policy sources to chat translation keys", () => {
    expect(retrievalParameterSourceLabelKey("USER_DEFAULTS")).toBe("retrievalSourceUserDefaults");
    expect(retrievalParameterSourceLabelKey("PROJECT_DEFAULTS")).toBe("retrievalSourceProjectDefaults");
    expect(retrievalParameterSourceLabelKey("PRESET_LOCKED")).toBe("retrievalSourcePresetLocked");
    expect(retrievalParameterSourceLabelKey("CONVERSATION_CUSTOM")).toBe("retrievalSourceConversationCustom");
  });
});
