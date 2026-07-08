import type { ChatRuntimeStateDto } from "@/types/api";

/** Minimal valid {@link ChatRuntimeStateDto} for component tests. */
export function mockChatRuntimeState(
  overrides: Partial<ChatRuntimeStateDto> = {},
): ChatRuntimeStateDto {
  const runtimeOverride = overrides.runtimeOverride ?? {};
  const manualOverrideKeys = overrides.manualOverrideKeys ?? Object.keys(runtimeOverride);
  const isCustom = overrides.isCustom ?? manualOverrideKeys.length > 0;
  const configurationMode =
    overrides.configurationMode ?? (isCustom ? "CUSTOM" : "PRESET");

  return {
    conversationId: "c1",
    selectedPresetId: null,
    effectivePresetId: "preset-default",
    preset: {
      kind: "DEFAULT",
      code: null,
      label: "Default",
      chatSelectable: true,
      supported: true,
      supportStatus: null,
      reasonIfUnsupported: null,
    },
    baseEffectiveConfig: {},
    effectiveConfig: {},
    conversationLlmModel: null,
    conversationClassifierModelId: null,
    conversationModelsPinned: false,
    configurationMode,
    runtimeOverride,
    manualOverrideKeys,
    isCustom,
    validation: { valid: true, supported: true, errors: [], warnings: [] },
    selectedWorkflow: null,
    indexCompatibility: null,
    requiresReindex: false,
    disabledRuntimeFeatures: [],
    ...overrides,
  };
}
