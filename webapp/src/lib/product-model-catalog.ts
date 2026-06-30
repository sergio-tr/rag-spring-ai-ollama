import type { MeSelectableLlmModelDto } from "@/types/api";

export type ProductModelCapability = "CHAT" | "EMBEDDING";

/** Maps selectable catalog rows to config form options (settings, chat, projects). */
export function toConfigModelOptions(models: MeSelectableLlmModelDto[]): ReadonlyArray<{
  value: string;
  label: string;
  disabled: boolean;
}> {
  return models.map((model) => ({
    value: model.modelName,
    label: model.displayName?.trim() ? model.displayName : model.modelName,
    disabled: !model.selectable,
  }));
}

/** Model ids that are available for normal UI selection. */
export function selectableCatalogModelIds(models: MeSelectableLlmModelDto[]): string[] {
  return models.filter((m) => m.selectable).map((m) => m.modelName);
}
