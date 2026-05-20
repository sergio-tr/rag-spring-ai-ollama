/** Project-scoped Chat model preferences (survives conversation switches in the same browser). */

const LLM_KEY_PREFIX = "chat-llm-model-preference-v1:";
const CLASSIFIER_KEY_PREFIX = "chat-classifier-model-preference-v1:";

function storageKey(prefix: string, projectId: string): string {
  return `${prefix}${projectId}`;
}

export function readProjectLlmModelPreference(projectId: string | null | undefined): string {
  if (!projectId || typeof globalThis.localStorage === "undefined") return "";
  try {
    return globalThis.localStorage.getItem(storageKey(LLM_KEY_PREFIX, projectId))?.trim() ?? "";
  } catch {
    return "";
  }
}

export function writeProjectLlmModelPreference(projectId: string | null | undefined, model: string): void {
  if (!projectId || typeof globalThis.localStorage === "undefined") return;
  try {
    const trimmed = model.trim();
    const key = storageKey(LLM_KEY_PREFIX, projectId);
    if (!trimmed) {
      globalThis.localStorage.removeItem(key);
    } else {
      globalThis.localStorage.setItem(key, trimmed);
    }
  } catch {
    // ignore quota / private mode
  }
}

export function readProjectClassifierModelPreference(projectId: string | null | undefined): string {
  if (!projectId || typeof globalThis.localStorage === "undefined") return "";
  try {
    return globalThis.localStorage.getItem(storageKey(CLASSIFIER_KEY_PREFIX, projectId))?.trim() ?? "";
  } catch {
    return "";
  }
}

export function writeProjectClassifierModelPreference(projectId: string | null | undefined, tag: string): void {
  if (!projectId || typeof globalThis.localStorage === "undefined") return;
  try {
    const trimmed = tag.trim();
    const key = storageKey(CLASSIFIER_KEY_PREFIX, projectId);
    if (!trimmed) {
      globalThis.localStorage.removeItem(key);
    } else {
      globalThis.localStorage.setItem(key, trimmed);
    }
  } catch {
    // ignore
  }
}
