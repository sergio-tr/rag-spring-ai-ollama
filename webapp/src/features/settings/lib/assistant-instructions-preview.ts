export type InstructionLayerId =
  | "system"
  | "answer"
  | "sourceUsage"
  | "grounding"
  | "abstention";

export type InstructionLayerStatus = "set" | "default" | "not_applicable";

export type InstructionLayerPreview = Readonly<{
  id: InstructionLayerId;
  labelKey: string;
  status: InstructionLayerStatus;
  preview: string | null;
}>;

const PREVIEW_MAX = 240;

function trimPreview(text: string): string {
  const trimmed = text.trim();
  if (!trimmed) return "";
  if (trimmed.length <= PREVIEW_MAX) return trimmed;
  return `${trimmed.slice(0, PREVIEW_MAX - 1).trimEnd()}…`;
}

function layerStatus(value: string | null | undefined): InstructionLayerStatus {
  return value != null && value.trim().length > 0 ? "set" : "default";
}

/** Product-facing summary of editable instruction layers (no internal prompt keys). */
export function buildAssistantInstructionsPreview(input: Readonly<{
  mode: "user" | "project";
  systemInstructions?: string | null;
  answerInstructions?: string | null;
  sourceUsageInstructions?: string | null;
}>): InstructionLayerPreview[] {
  const system = input.systemInstructions ?? "";
  const answer = input.answerInstructions ?? "";
  const source = input.sourceUsageInstructions ?? "";

  const layers: InstructionLayerPreview[] = [
    {
      id: "system",
      labelKey: "instructionsSystemLabel",
      status: layerStatus(system),
      preview: system.trim() ? trimPreview(system) : null,
    },
    {
      id: "answer",
      labelKey: "instructionsAnswerLabel",
      status: input.mode === "user" ? layerStatus(answer) : "not_applicable",
      preview: input.mode === "user" && answer.trim() ? trimPreview(answer) : null,
    },
    {
      id: "sourceUsage",
      labelKey: "instructionsSourceUsageLabel",
      status: input.mode === "project" ? layerStatus(source) : "not_applicable",
      preview: input.mode === "project" && source.trim() ? trimPreview(source) : null,
    },
    {
      id: "grounding",
      labelKey: "instructionsGroundingLabel",
      status: "default",
      preview: null,
    },
    {
      id: "abstention",
      labelKey: "instructionsAbstentionLabel",
      status: "default",
      preview: null,
    },
  ];

  return layers;
}

/** Keys that must never appear as user-editable instruction fields. */
export const FORBIDDEN_INSTRUCTION_EDITOR_KEYS = [
  "queryRewritePrompt",
  "memoryCondensePrompt",
  "judgePrompt",
  "factualVerifierPrompt",
  "metadataToolPrompt",
  "evaluationJudgePrompt",
  "promptBundle",
  "promptBundleSha256",
  "PromptBundleFingerprint",
] as const;
