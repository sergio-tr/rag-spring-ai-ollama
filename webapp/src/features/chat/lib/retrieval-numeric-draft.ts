export type NumericDraft = {
  text: string;
  parsedValue: number | null;
  isValid: boolean;
  error?: "invalid" | "range" | "required";
};

export type TopKConstraints = {
  min: number;
  max: number;
};

export type SimilarityConstraints = {
  min: number;
  max: number;
};

const PARTIAL_INTEGER = /^\d*$/;
const PARTIAL_DECIMAL = /^-?\d*\.?\d*$/;

export function formatNumericDraftValue(value: number): string {
  return String(value);
}

export function createNumericDraftFromValue(value: number): NumericDraft {
  const text = formatNumericDraftValue(value);
  return {
    text,
    parsedValue: value,
    isValid: true,
  };
}

export function parseTopKDraft(text: string, constraints: TopKConstraints): NumericDraft {
  if (text === "") {
    return { text, parsedValue: null, isValid: false, error: "required" };
  }
  if (!PARTIAL_INTEGER.test(text)) {
    return { text, parsedValue: null, isValid: false, error: "invalid" };
  }
  const parsed = Number(text);
  if (!Number.isFinite(parsed) || !Number.isInteger(parsed)) {
    return { text, parsedValue: null, isValid: false, error: "invalid" };
  }
  if (parsed < constraints.min || parsed > constraints.max) {
    return { text, parsedValue: parsed, isValid: false, error: "range" };
  }
  return { text, parsedValue: parsed, isValid: true };
}

function isPartialDecimalDraft(text: string): boolean {
  if (text === "" || text === "-" || text === "." || text === "-.") {
    return true;
  }
  if (text.endsWith(".")) {
    return true;
  }
  return false;
}

export function parseSimilarityDraft(text: string, constraints: SimilarityConstraints): NumericDraft {
  if (text === "") {
    return { text, parsedValue: null, isValid: false, error: "required" };
  }
  if (!PARTIAL_DECIMAL.test(text)) {
    return { text, parsedValue: null, isValid: false, error: "invalid" };
  }
  if (isPartialDecimalDraft(text)) {
    return { text, parsedValue: null, isValid: false };
  }
  const parsed = Number(text);
  if (!Number.isFinite(parsed)) {
    return { text, parsedValue: null, isValid: false, error: "invalid" };
  }
  if (parsed < constraints.min || parsed > constraints.max) {
    return { text, parsedValue: parsed, isValid: false, error: "range" };
  }
  return { text, parsedValue: parsed, isValid: true };
}

export function shouldShowDraftError(
  draft: NumericDraft,
  options: { focused: boolean; touched: boolean },
): boolean {
  if (draft.isValid) {
    return false;
  }
  if (draft.error === "invalid" || draft.error === "range") {
    return true;
  }
  if (draft.error === "required") {
    return options.touched && !options.focused;
  }
  return false;
}
