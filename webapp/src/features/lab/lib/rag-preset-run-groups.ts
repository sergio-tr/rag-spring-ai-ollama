import type { ExperimentalPresetCatalogItemDto } from "@/types/api";

export type RagPresetRunGroupKey =
  | "DIRECT_LLM"
  | "NO_INDEX"
  | "DOCUMENT_LEVEL"
  | "CHUNK_LEVEL"
  | "CHUNK_LEVEL_METADATA"
  | "HYBRID_METADATA"
  | "MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN";

export type RagPresetMaterializationGroup = {
  groupKey: RagPresetRunGroupKey;
  presetCodes: string[];
  materializationStrategy: string | null;
  requiresMetadata: boolean;
};

const GROUP_ORDER: RagPresetRunGroupKey[] = [
  "DIRECT_LLM",
  "NO_INDEX",
  "DOCUMENT_LEVEL",
  "CHUNK_LEVEL",
  "CHUNK_LEVEL_METADATA",
  "HYBRID_METADATA",
  "MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN",
];

export function presetRunGroupKeyFor(
  preset: ExperimentalPresetCatalogItemDto,
): RagPresetRunGroupKey {
  if (preset.requiresMultiTurn || preset.singleTurnBenchmarkSelectable === false) {
    return "MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN";
  }
  if (preset.code === "P0") return "DIRECT_LLM";
  if (preset.code === "P1") return "NO_INDEX";

  const mat = preset.indexRequirements?.requiredMaterializationStrategy ?? null;
  const metadata = preset.indexRequirements?.requiresMetadataSupport === true;

  if (!mat || mat === "NONE") return "NO_INDEX";
  if (mat === "DOCUMENT_LEVEL") return "DOCUMENT_LEVEL";
  if (mat === "CHUNK_LEVEL") return metadata ? "CHUNK_LEVEL_METADATA" : "CHUNK_LEVEL";
  if (mat === "HYBRID") return "HYBRID_METADATA";
  return "NO_INDEX";
}

export function materializationLabelForGroup(groupKey: RagPresetRunGroupKey): string | null {
  switch (groupKey) {
    case "DOCUMENT_LEVEL":
      return "DOCUMENT_LEVEL";
    case "CHUNK_LEVEL":
    case "CHUNK_LEVEL_METADATA":
      return "CHUNK_LEVEL";
    case "HYBRID_METADATA":
      return "HYBRID";
    default:
      return null;
  }
}

export function buildRagPresetMaterializationGroups(
  selectedCodes: readonly string[],
  catalog: readonly ExperimentalPresetCatalogItemDto[] | undefined,
): RagPresetMaterializationGroup[] {
  if (selectedCodes.length === 0) return [];
  const byCode = new Map((catalog ?? []).map((p) => [p.code, p]));
  const buckets = new Map<RagPresetRunGroupKey, string[]>();

  for (const raw of selectedCodes) {
    const code = raw.trim();
    if (!code) continue;
    const preset = byCode.get(code);
    const groupKey = preset ? presetRunGroupKeyFor(preset) : "NO_INDEX";
    const list = buckets.get(groupKey) ?? [];
    list.push(code);
    buckets.set(groupKey, list);
  }

  return GROUP_ORDER.filter((gk) => buckets.has(gk)).map((groupKey) => {
    const presetCodes = [...(buckets.get(groupKey) ?? [])].sort((a, b) => {
      const pa = byCode.get(a);
      const pb = byCode.get(b);
      const ia = pa?.protocolStageIndex ?? Number.MAX_SAFE_INTEGER;
      const ib = pb?.protocolStageIndex ?? Number.MAX_SAFE_INTEGER;
      if (ia !== ib) return ia - ib;
      return a.localeCompare(b);
    });
    const representative = byCode.get(presetCodes[0] ?? "");
    return {
      groupKey,
      presetCodes,
      materializationStrategy: materializationLabelForGroup(groupKey),
      requiresMetadata:
        groupKey === "CHUNK_LEVEL_METADATA" ||
        groupKey === "HYBRID_METADATA" ||
        representative?.indexRequirements?.requiresMetadataSupport === true,
    };
  });
}
