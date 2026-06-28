import type { LabJobEventDto } from "@/types/api";

/** Compact user-facing line for a Lab SSE subtask/progress event. */
export function formatLabJobEventLine(event: LabJobEventDto): string {
  const message = event.message?.trim();
  if (message) {
    const preset = event.currentPresetCode?.trim();
    const model = event.currentModelId?.trim();
    const suffix =
      preset && model ? ` (${preset} · ${model})` : preset ? ` (${preset})` : model ? ` (${model})` : "";
    return `${message}${suffix}`;
  }
  const globalDone = event.globalCompletedItems;
  const globalTotal = event.globalTotalItems;
  if (globalTotal != null && globalTotal > 0 && globalDone != null) {
    return `Item ${globalDone}/${globalTotal}`;
  }
  const runDone = event.runCompletedItems;
  const runTotal = event.runTotalItems;
  if (runTotal != null && runTotal > 0 && runDone != null) {
    return `Item ${runDone}/${runTotal}`;
  }
  return event.type.replaceAll("_", " ").toLowerCase();
}
