/** Preset-controlled Chat runtime feature locking (Phase 2.5). */

const PRESET_CONTROLLED_BOOLEAN_KEYS = new Set([
  "useRetrieval",
  "naiveFullCorpusInPromptEnabled",
  "expansionEnabled",
  "nerEnabled",
  "toolsEnabled",
  "functionCallingEnabled",
  "useAdvisor",
  "reasoningEnabled",
  "rankerEnabled",
  "postRetrievalEnabled",
  "adaptiveRoutingEnabled",
  "judgeEnabled",
  "clarificationEnabled",
  "memoryEnabled",
]);

function coerceBool(v: unknown): boolean {
  return v === true || v === "true";
}

export function isPresetControlledBooleanKey(key: string): boolean {
  return PRESET_CONTROLLED_BOOLEAN_KEYS.has(key);
}

/** Feature is required by the selected preset (locked on). */
export function isPresetBaseFeature(key: string, presetBaseEffectiveConfig: Record<string, unknown> | null | undefined): boolean {
  if (!presetBaseEffectiveConfig || !isPresetControlledBooleanKey(key)) return false;
  return coerceBool(presetBaseEffectiveConfig[key]);
}

/** Optional add-on toggling deferred — preset has feature off, Chat cannot enable. */
export function isPresetControlledOffFeature(
  key: string,
  presetBaseEffectiveConfig: Record<string, unknown> | null | undefined,
): boolean {
  if (!presetBaseEffectiveConfig || !isPresetControlledBooleanKey(key)) return false;
  if (!Object.prototype.hasOwnProperty.call(presetBaseEffectiveConfig, key)) return false;
  return !coerceBool(presetBaseEffectiveConfig[key]);
}

export function presetBaseFeatures(presetBaseEffectiveConfig: Record<string, unknown> | null | undefined): string[] {
  if (!presetBaseEffectiveConfig) return [];
  return [...PRESET_CONTROLLED_BOOLEAN_KEYS].filter((key) => coerceBool(presetBaseEffectiveConfig[key]));
}
