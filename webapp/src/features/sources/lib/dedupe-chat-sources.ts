export type ChatSourceRecord = Record<string, unknown>;

export type DedupedChatSourceGroup = {
  key: string;
  displayName: string;
  chunks: ChatSourceRecord[];
};

function sourceDisplayName(source: ChatSourceRecord, fallbackIndex: number): string {
  const value =
    source.fileName ?? source.filename ?? source.documentName ?? source.documentId ?? `source-${fallbackIndex + 1}`;
  return String(value);
}

function sourceGroupKey(source: ChatSourceRecord, displayName: string): string {
  const docId = source.documentId ?? source.projectDocumentId;
  if (typeof docId === "string" && docId.trim()) return docId.trim();
  return displayName;
}

/**
 * Display-level deduplication: one principal row per document with optional chunk expansion.
 * Backend source arrays are preserved elsewhere for traceability.
 */
export function dedupeChatSourcesForDisplay(sources: unknown[]): DedupedChatSourceGroup[] {
  const groups = new Map<string, DedupedChatSourceGroup>();

  sources.forEach((raw, index) => {
    const source = (raw ?? {}) as ChatSourceRecord;
    const displayName = sourceDisplayName(source, index);
    const key = sourceGroupKey(source, displayName);
    const existing = groups.get(key);
    if (existing) {
      existing.chunks.push(source);
      return;
    }
    groups.set(key, { key, displayName, chunks: [source] });
  });

  return [...groups.values()];
}
