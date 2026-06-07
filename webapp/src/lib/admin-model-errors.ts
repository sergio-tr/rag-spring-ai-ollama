type TranslateFn = (key: string) => string;

/** Maps backend admin model check/delete codes to user-facing copy (no HTTP/API jargon). */
export function adminModelUserMessage(code: string | null | undefined, t: TranslateFn): string {
  switch (code) {
    case "MODEL_NOT_FOUND":
      return t("probeNotInstalled");
    case "MODEL_EMBEDDING_PROBE_FAILED":
    case "MODEL_TYPE_MISMATCH":
      return t("probeEmbeddingFailed");
    case "MODEL_PULL_FAILED":
      return t("probePullFailed");
    case "OLLAMA_UNAVAILABLE":
      return t("probeOllamaUnreachable");
    default:
      return t("modelCheckError");
  }
}

export function adminModelCheckSummary(
  res: {
    existsLocal: boolean;
    embeddingProbeOk: boolean;
    requestedType: "LLM" | "EMBEDDING";
    errorCode: string | null;
  },
  t: TranslateFn,
): string {
  if (!res.existsLocal) {
    return t("probeNotInstalled");
  }
  if (res.requestedType === "EMBEDDING" && !res.embeddingProbeOk) {
    return adminModelUserMessage(res.errorCode, t);
  }
  return t("probeOk");
}
