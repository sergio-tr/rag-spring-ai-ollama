import { useCallback, useEffect, useRef } from "react";

/**
 * Tracks the local conversation configuration draft while PATCH requests are in flight.
 * The backend merges each partial config patch into the persisted full snapshot.
 */
export function useConversationConfigurationDraft(
  persistedConfiguration: Record<string, unknown>,
  conversationId: string | null | undefined,
  patchPending: boolean,
) {
  const draftRef = useRef<Record<string, unknown>>({});
  const conversationIdRef = useRef(conversationId);

  useEffect(() => {
    if (conversationIdRef.current !== conversationId) {
      conversationIdRef.current = conversationId;
      draftRef.current = { ...persistedConfiguration };
    }
  }, [conversationId, persistedConfiguration]);

  useEffect(() => {
    if (!patchPending) {
      draftRef.current = { ...persistedConfiguration };
    }
  }, [persistedConfiguration, patchPending]);

  const applyBooleanPatch = useCallback((key: string, value: boolean) => {
    const merged = { ...draftRef.current, [key]: value };
    draftRef.current = merged;
    return { [key]: value };
  }, []);

  const applyPatch = useCallback((patch: Record<string, unknown>) => {
    const merged = { ...draftRef.current, ...patch };
    draftRef.current = merged;
    return patch;
  }, []);

  const replaceDraft = useCallback((next: Record<string, unknown>) => {
    draftRef.current = { ...next };
    return draftRef.current;
  }, []);

  const getSnapshot = useCallback(() => ({ ...draftRef.current }), []);

  return { applyBooleanPatch, applyPatch, replaceDraft, getSnapshot };
}

/** @deprecated Use useConversationConfigurationDraft */
export const useRuntimeOverrideDraft = useConversationConfigurationDraft;
