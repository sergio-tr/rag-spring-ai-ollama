import type { PresetCopyFn } from "@/lib/product-preset-labels";

/** Shared preset display keys live under the Chat namespace (`presetDisplay`, `presetLatencyTier`). */
export function createPresetCopyFn(primaryT: PresetCopyFn, chatT: PresetCopyFn): PresetCopyFn {
  return (key: string) => {
    if (key.startsWith("chat") || key.startsWith("presetDisplay") || key.startsWith("presetLatencyTier")) {
      return chatT(key);
    }
    return primaryT(key);
  };
}
