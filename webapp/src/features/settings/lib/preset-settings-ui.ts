/** When false (default), Settings → Presets is catalog-only for TFG closure (no custom preset builder). */
export function isPresetCreationUiEnabled(): boolean {
  return process.env.NEXT_PUBLIC_PRESET_CREATION_ENABLED === "true";
}
