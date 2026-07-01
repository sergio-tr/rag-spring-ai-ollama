/** Forbidden phrases in normal user-facing copy (not code identifiers). Client-safe — no Node APIs. */
export const FORBIDDEN_PRODUCT_VISIBLE_PATTERNS: ReadonlyArray<{
  readonly id: string;
  readonly pattern: RegExp;
}> = [
  { id: "Demo_Best", pattern: /\bDemo_Best\b/ },
  { id: "Demo_Worst", pattern: /\bDemo_Worst\b/ },
  { id: "Demo_NaiveFullCorpus", pattern: /\bDemo_NaiveFullCorpus\b/ },
  { id: "experimental preset", pattern: /\bexperimental preset\b/i },
  { id: "preset code", pattern: /\bpreset code\b/i },
  { id: "profile hash", pattern: /\bprofile hash\b/i },
  { id: "prompt bundle hash", pattern: /\bprompt bundle hash\b/i },
  { id: "runtime override", pattern: /\bruntime override\b/i },
  { id: "resolved config", pattern: /\bresolved config\b/i },
  { id: "deterministic tool", pattern: /\bdeterministic tool\b/i },
  { id: "function calling takes precedence", pattern: /function calling takes precedence/i },
] as const;
