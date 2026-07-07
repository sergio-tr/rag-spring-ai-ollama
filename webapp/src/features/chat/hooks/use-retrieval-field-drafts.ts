import { useCallback, useEffect, useState } from "react";
import {
  createNumericDraftFromValue,
  parseSimilarityDraft,
  parseTopKDraft,
  type SimilarityConstraints,
  type TopKConstraints,
} from "@/features/chat/lib/retrieval-numeric-draft";
import type { RetrievalOverrideMode } from "@/features/chat/lib/retrieval-override-mode";

type UseRetrievalFieldDraftsOptions = {
  committedTopK: number;
  committedSimilarity: number;
  retrievalOverrideMode: RetrievalOverrideMode;
  conversationId: string | null | undefined;
  patchPending: boolean;
  topKConstraints: TopKConstraints;
  similarityConstraints: SimilarityConstraints;
  onEnsureCustomMode: () => void;
  onCommitTopK: (value: number) => void;
  onCommitSimilarity: (value: number) => void;
};

export function useRetrievalFieldDrafts({
  committedTopK,
  committedSimilarity,
  retrievalOverrideMode,
  conversationId,
  patchPending,
  topKConstraints,
  similarityConstraints,
  onEnsureCustomMode,
  onCommitTopK,
  onCommitSimilarity,
}: UseRetrievalFieldDraftsOptions) {
  const [topKDraft, setTopKDraft] = useState(() => createNumericDraftFromValue(committedTopK));
  const [similarityDraft, setSimilarityDraft] = useState(() =>
    createNumericDraftFromValue(committedSimilarity),
  );
  const [topKFocused, setTopKFocused] = useState(false);
  const [similarityFocused, setSimilarityFocused] = useState(false);
  const [topKTouched, setTopKTouched] = useState(false);
  const [similarityTouched, setSimilarityTouched] = useState(false);
  const [editingCustomMode, setEditingCustomMode] = useState(false);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reset drafts when conversation changes
    setTopKDraft(createNumericDraftFromValue(committedTopK));
    setSimilarityDraft(createNumericDraftFromValue(committedSimilarity));
    setTopKFocused(false);
    setSimilarityFocused(false);
    setTopKTouched(false);
    setSimilarityTouched(false);
    setEditingCustomMode(false);
  }, [conversationId, committedTopK, committedSimilarity]);

  useEffect(() => {
    if (!topKFocused && !patchPending) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- sync draft from committed value when not editing
      setTopKDraft(createNumericDraftFromValue(committedTopK));
    }
  }, [committedTopK, topKFocused, patchPending]);

  useEffect(() => {
    if (!similarityFocused && !patchPending) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- sync draft from committed value when not editing
      setSimilarityDraft(createNumericDraftFromValue(committedSimilarity));
    }
  }, [committedSimilarity, similarityFocused, patchPending]);

  useEffect(() => {
    if (retrievalOverrideMode !== "custom") {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- leave custom edit state when mode changes
      setEditingCustomMode(false);
    }
  }, [retrievalOverrideMode]);

  const beginCustomEdit = useCallback(() => {
    setEditingCustomMode(true);
    onEnsureCustomMode();
  }, [onEnsureCustomMode]);

  const handleTopKFocus = useCallback(() => {
    setTopKFocused(true);
    beginCustomEdit();
  }, [beginCustomEdit]);

  const handleSimilarityFocus = useCallback(() => {
    setSimilarityFocused(true);
    beginCustomEdit();
  }, [beginCustomEdit]);

  const handleTopKChange = useCallback(
    (text: string) => {
      setTopKTouched(true);
      beginCustomEdit();
      const nextDraft = parseTopKDraft(text, topKConstraints);
      setTopKDraft(nextDraft);
      if (nextDraft.isValid && nextDraft.parsedValue != null) {
        onCommitTopK(nextDraft.parsedValue);
      }
    },
    [beginCustomEdit, onCommitTopK, topKConstraints],
  );

  const handleSimilarityChange = useCallback(
    (text: string) => {
      setSimilarityTouched(true);
      beginCustomEdit();
      const nextDraft = parseSimilarityDraft(text, similarityConstraints);
      setSimilarityDraft(nextDraft);
      if (nextDraft.isValid && nextDraft.parsedValue != null) {
        onCommitSimilarity(nextDraft.parsedValue);
      }
    },
    [beginCustomEdit, onCommitSimilarity, similarityConstraints],
  );

  const handleTopKBlur = useCallback(() => {
    setTopKFocused(false);
    setTopKTouched(true);
  }, []);

  const handleSimilarityBlur = useCallback(() => {
    setSimilarityFocused(false);
    setSimilarityTouched(true);
  }, []);

  const displayRetrievalMode: RetrievalOverrideMode =
    editingCustomMode || retrievalOverrideMode === "custom" ? "custom" : retrievalOverrideMode;

  return {
    topKDraft,
    similarityDraft,
    topKFocused,
    similarityFocused,
    topKTouched,
    similarityTouched,
    displayRetrievalMode,
    handleTopKFocus,
    handleSimilarityFocus,
    handleTopKChange,
    handleSimilarityChange,
    handleTopKBlur,
    handleSimilarityBlur,
  };
}

export type RetrievalFieldDrafts = ReturnType<typeof useRetrievalFieldDrafts>;
