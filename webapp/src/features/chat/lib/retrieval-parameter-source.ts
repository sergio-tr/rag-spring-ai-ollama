export type RetrievalParameterPolicySource =
  | "USER_DEFAULTS"
  | "PROJECT_DEFAULTS"
  | "PRESET_LOCKED"
  | "CONVERSATION_CUSTOM";

export function retrievalParameterSourceLabelKey(
  source: RetrievalParameterPolicySource | string | null | undefined,
): string {
  switch (source) {
    case "PRESET_LOCKED":
      return "retrievalSourcePresetLocked";
    case "CONVERSATION_CUSTOM":
      return "retrievalSourceConversationCustom";
    case "PROJECT_DEFAULTS":
      return "retrievalSourceProjectDefaults";
    case "USER_DEFAULTS":
    default:
      return "retrievalSourceUserDefaults";
  }
}
