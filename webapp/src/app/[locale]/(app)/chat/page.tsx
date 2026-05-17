"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { HelpPopover } from "@/features/help/HelpPopover";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import { ChatConversationDocumentsSheet } from "@/features/chat/components/ChatConversationDocumentsSheet";
import { DeleteConversationDialog } from "@/features/chat/components/DeleteConversationDialog";
import { MoveConversationDialog } from "@/features/chat/components/MoveConversationDialog";
import { NewConversationDialog } from "@/features/chat/components/NewConversationDialog";
import {
  useConversationMessages,
  useConversations,
  useCreateConversation,
  usePatchConversation,
} from "@/features/chat/hooks/use-conversations";
import { useChatRuntimeState } from "@/features/chat/hooks/use-chat-runtime-state";
import { optimisticConsumed } from "@/features/chat/lib/chat-optimistic";
import { useModelsCatalog } from "@/features/chat/hooks/use-models-catalog";
import { useChatPresetsCatalog } from "@/features/chat/hooks/use-chat-presets-catalog";
import {
  useProjectDocumentsForConversation,
  useUploadConversationOverlayDocument,
} from "@/features/documents/hooks/use-project-documents";
import { useProjectList } from "@/features/projects/hooks/use-projects";
import { useSyncActiveProjectFromChatUrl } from "@/features/projects/hooks/use-sync-active-project-from-chat-url";
import { buildProjectScopedChatHref } from "@/features/projects/lib/open-project-navigation";
import { ProjectVisual } from "@/features/projects/components/ProjectVisual";
import {
  ApiError,
  apiFetch,
  apiProductPath,
  getSafeApiErrorMessage,
  sanitizePlainErrorTextForUi,
} from "@/lib/api-client";
import { resolveChatJobFailureUserHint } from "@/features/chat/lib/chat-job-errors";
import { followLabJob } from "@/lib/lab-job-follow";
import { cn } from "@/lib/utils";
import { Link, useRouter } from "@/navigation";
import { useAppStore } from "@/store/app.store";
import { useChatExplainStore } from "@/store/chat-explain.store";
import { useTraceStore } from "@/features/trace/trace.store";
import type {
  ConversationDraftDto,
  LabJobAcceptedDto,
  MessageDto,
  PatchUserMessageBody,
  PostMessageBody,
  ProjectDocumentDto,
  StreamDonePayload,
} from "@/types/api";
import { ChevronDown, PanelLeftClose, PanelLeftOpen, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { ChatConfigurationSidePanel } from "@/features/chat/components/ChatConfigurationSidePanel";
import { useChatConfigurationPanelStore } from "@/features/chat/store/chat-configuration-panel.store";

const CHAT_CONV_LIST_COLLAPSED_KEY = "chat-conv-list-collapsed";

type AssistantPipelinePhase =
  | "sending"
  | "contacting"
  | "processing"
  | "receiving"
  | "failed"
  | null;

async function cancelChatJob(jobId: string, signal?: AbortSignal): Promise<void> {
  await apiFetch<void>(apiProductPath(`/lab/jobs/${jobId}/cancel`), {
    method: "POST",
    signal,
  });
}

function coerceBool(v: unknown): boolean {
  return v === true || v === "true";
}

function firstRuntimeBlockingMessage(runtimeState: {
  isValid?: boolean;
  blockingIssues?: Array<{ message?: string | null }>;
  validation?: { valid: boolean; supported: boolean; errors: Array<{ message?: string | null }> };
} | null): string | null {
  if (!runtimeState) return null;
  const issues = runtimeState.blockingIssues ?? runtimeState.validation?.errors ?? [];
  const first = issues.find((i) => typeof i.message === "string" && i.message.trim() !== "");
  if (first?.message) return first.message;
  const valid =
    runtimeState.isValid ??
    (runtimeState.validation ? runtimeState.validation.valid && runtimeState.validation.supported : true);
  return valid ? null : "Configuration is invalid. Open Chat configuration to resolve it.";
}

function streamDonePayloadFromAssistantMessage(m: MessageDto): StreamDonePayload {
  const meta = m.executionMetadata ?? {};
  const telemetry: Record<string, unknown> = {};
  const keys = [
    "reasoningAttempted",
    "reasoningStrategy",
    "reasoningPlanSummaryTruncated",
    "retrievalRerankApplied",
    "retrievalDenseCandidateCount",
    "retrievalAfterFusionCount",
    "retrievalBeforePostRetrievalCount",
    "retrievalAfterRerankCount",
    "retrievalAfterFilterCount",
    "retrievalAfterCompressionCount",
    "retrievalProtectedCandidateCount",
    "retrievalDroppedCandidateCount",
    "retrievalRerankScoreSummaryTruncated",
    "memoryAttempted",
    "memoryOutcome",
    "memoryCondensationUsed",
    "routingAttempted",
    "routingRouteKind",
    "routingOutcome",
    "routingFallbackApplied",
    "routingFallbackRouteKind",
    "judgeAttempted",
    "judgeFinalOutcome",
    "judgeFinalAnswerFromRetry",
    "judgeCandidateSource",
    "clarificationRequired",
    "clarificationOutcome",
  ];
  for (const k of keys) {
    if (meta[k] !== undefined && meta[k] !== null) {
      telemetry[k] = meta[k];
    }
  }
  return {
    answer: m.content,
    queryType: m.queryType,
    usedTool: false,
    toolUsed: null,
    sources: Array.isArray(m.sources) ? m.sources : [],
    pipelineSteps: Array.isArray(m.pipelineSteps) ? m.pipelineSteps : [],
    runtimeTelemetry: Object.keys(telemetry).length > 0 ? telemetry : undefined,
  };
}

function isAssistantClarificationTurn(m: MessageDto): boolean {
  return m.role === "ASSISTANT" && coerceBool(m.executionMetadata?.clarificationRequired);
}

function isAssistantRetryable(status: string | null | undefined): boolean {
  return status === "ERROR" || status === "CANCELLED";
}

/** Defers draft hydration so nested callbacks stay shallow (Sonar nesting limit). */
function scheduleHydrateComposerFromDraft(
  content: string,
  isCancelled: () => boolean,
  setInput: Dispatch<SetStateAction<string>>,
): void {
  setTimeout(() => {
    if (isCancelled()) return;
    setInput((prev) => (prev.trim() === "" ? content : prev));
  }, 0);
}

function ChatPageInner() {
  const t = useTranslations("Chat");
  const tHelp = useTranslations("Help");
  const router = useRouter();
  const searchParams = useSearchParams();
  const active = useAppStore((s) => s.activeProject);
  const projectId = active?.id;
  const urlProjectId = searchParams?.get?.("projectId")?.trim() || null;
  useSyncActiveProjectFromChatUrl(urlProjectId);
  const urlConversationId = searchParams?.get?.("conversationId") ?? null;
  const [conversationId, setConversationId] = useState<string | null>(() => urlConversationId ?? null);
  const [convListCollapsed, setConvListCollapsed] = useState(() => {
    try {
      return sessionStorage.getItem(CHAT_CONV_LIST_COLLAPSED_KEY) === "true";
    } catch {
      return false;
    }
  });
  const [input, setInput] = useState("");
  const [sendError, setSendError] = useState<string | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [editingUserMessageId, setEditingUserMessageId] = useState<string | null>(null);
  const [editBody, setEditBody] = useState("");
  /** Empty string = backend default model. Scoped to the conversation edited locally. */
  const [llmModelChoiceDraft, setLlmModelChoiceDraft] = useState<{ conversationId: string | null; value: string }>(
    { conversationId: null, value: "" },
  );
  /** Empty string = no per-conversation classifier override (project RAG JSON applies). */
  const [classifierModelChoiceDraft, setClassifierModelChoiceDraft] = useState<{
    conversationId: string | null;
    value: string;
  }>({ conversationId: null, value: "" });
  /** Scoped to `conversationId` so switching chats does not require clearing in an effect. */
  const [limitDocsNoticeRecord, setLimitDocsNoticeRecord] = useState<{
    conversationId: string | null;
    message: string | null;
  }>({ conversationId: null, message: null });
  const limitDocsToggleNotice =
    conversationId &&
    limitDocsNoticeRecord.conversationId === conversationId &&
    limitDocsNoticeRecord.message
      ? limitDocsNoticeRecord.message
      : null;
  const [docsSheetOpen, setDocsSheetOpen] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadNotice, setUploadNotice] = useState<string | null>(null);
  const [uploadItems, setUploadItems] = useState<
    Array<{
      id: string;
      fileName: string;
      phase: "uploading" | "ingesting" | "ready" | "error" | "stalled";
      chunkCount?: number | null;
      errorMessage?: string | null;
      docId?: string | null;
      file?: File;
    }>
  >([]);
  const [deleteDialogTarget, setDeleteDialogTarget] = useState<{ id: string; title: string } | null>(
    null,
  );
  const [moveDialogOpen, setMoveDialogOpen] = useState(false);
  const [newConvWizardOpen, setNewConvWizardOpen] = useState(false);
  const configPanelOpen = useChatConfigurationPanelStore((s) => s.open);
  const setConfigPanelOpen = useChatConfigurationPanelStore((s) => s.setOpen);
  const abortRef = useRef<AbortController | null>(null);
  const activeJobIdRef = useRef<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const stickToBottomRef = useRef(true);
  const [showJumpToBottom, setShowJumpToBottom] = useState(false);
  const [titleDraft, setTitleDraft] = useState("");
  const draftSaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** POST + async chat job in flight (distinct from streaming preview state). */
  const [isSending, setIsSending] = useState(false);
  /** Local-only user bubble until the server list includes the same text. */
  const [optimisticUserContent, setOptimisticUserContent] = useState<string | null>(null);
  /** Visible pipeline stages between send and a terminal assistant outcome. */
  const [assistantPhase, setAssistantPhase] = useState<AssistantPipelinePhase>(null);
  const [chatDropActive, setChatDropActive] = useState(false);

  useEffect(() => {
    if (urlConversationId && urlConversationId !== conversationId) {
      const syncTimer = setTimeout(() => setConversationId(urlConversationId), 0);
      return () => clearTimeout(syncTimer);
    }
  }, [urlConversationId, conversationId]);

  const selectConversation = useCallback(
    (nextId: string) => {
      setConversationId(nextId);
      if (!projectId) return;
      router.push(buildProjectScopedChatHref(projectId, nextId));
    },
    [router, projectId],
  );

  function persistConvListCollapsed(next: boolean) {
    setConvListCollapsed(next);
    try {
      sessionStorage.setItem(CHAT_CONV_LIST_COLLAPSED_KEY, next ? "true" : "false");
    } catch {
      /* ignore */
    }
  }

  const { data: convs } = useConversations(projectId);
  const createConv = useCreateConversation(projectId);
  const patchConv = usePatchConversation(projectId);
  // R1: runtime-state is the authoritative steady-state validation source.
  const { data: messages, refetch: refetchMessages } = useConversationMessages(conversationId ?? undefined);
  /** Only the latest user turn can be edited (matches backend truncate-from semantics). */
  const lastUserMessageId = useMemo(() => {
    if (!messages?.length) return null;
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].role === "USER") return messages[i].id;
    }
    return null;
  }, [messages]);
  const { data: docs, refetch: refetchProjectDocuments } = useProjectDocumentsForConversation(projectId, conversationId);
  const uploadDoc = useUploadConversationOverlayDocument(projectId, conversationId);
  const { data: projectListData } = useProjectList(0, 64);
  const currentProject = useMemo(
    () => projectListData?.items?.find((p) => p.id === projectId),
    [projectListData?.items, projectId],
  );
  const { data: modelsCatalog, isError: modelsError, error: modelsQueryError } = useModelsCatalog();
  const chatPresetsCatalog = useChatPresetsCatalog();
  const presets = chatPresetsCatalog.data?.productPresets;
  const presetsError = chatPresetsCatalog.isError;
  const presetsLoading = chatPresetsCatalog.isLoading;
  const experimentalPresets = chatPresetsCatalog.data?.experimentalPresets;
  const experimentalPresetsLoading = chatPresetsCatalog.isLoading;
  const experimentalPresetsError = chatPresetsCatalog.isError;

  const activeConv = useMemo(
    () => (conversationId && convs ? convs.find((c) => c.id === conversationId) : undefined),
    [conversationId, convs],
  );
  const conversationNotFound = Boolean(conversationId && convs && !activeConv);

  const llmModelChoice =
    llmModelChoiceDraft.conversationId === activeConv?.id ? llmModelChoiceDraft.value : (activeConv?.llmModel ?? "");
  const classifierModelChoice =
    classifierModelChoiceDraft.conversationId === activeConv?.id
      ? classifierModelChoiceDraft.value
      : (activeConv?.classifierModelId ?? "");

  /** Single source of truth: server-backed conversation row (optimistic updates inside {@link usePatchConversation}). */
  const selectedDocIds = useMemo(() => activeConv?.documentFilter ?? [], [activeConv?.documentFilter]);
  const limitDocs = selectedDocIds.length > 0;
  const readyDocIds = useMemo(() => docs?.filter((d) => d.status === "READY").map((d) => d.id) ?? [], [docs]);
  const limitDocsDisabled = !limitDocs && readyDocIds.length === 0;
  const limitDocsToggleNoticeEffective =
    limitDocsDisabled && !limitDocs ? t("limitDocumentsNoReadyHint") : limitDocsToggleNotice;

  const presetsCatalogEmpty = !presetsError && presets?.length === 0;

  const syntheticPresetOptionNeeded = false;

  const presetSelectDisabled =
    !!presetsError || patchConv.isPending || presetsLoading || presetsCatalogEmpty;

  useEffect(() => {
    patchConv.reset();
  }, [conversationId]); /* eslint-disable-line react-hooks/exhaustive-deps -- reset patch mutation only when switching chats */

  useEffect(() => {
    const syncTimer = setTimeout(() => setTitleDraft(activeConv?.title ?? ""), 0);
    return () => clearTimeout(syncTimer);
  }, [activeConv?.id, activeConv?.title]);

  const runtimeStateQuery = useChatRuntimeState(conversationId);
  const runtimeState = runtimeStateQuery.data ?? null;
  const presetSelectValue = runtimeState?.selectedPresetId ?? "";
  const runtimeBlockingMessage = firstRuntimeBlockingMessage(runtimeState);
  const runtimeStateInvalid = Boolean(runtimeBlockingMessage);

  const setLastDone = useChatExplainStore((s) => s.setLastDone);
  const setStreamingText = useChatExplainStore((s) => s.setStreamingText);
  const resetStreaming = useChatExplainStore((s) => s.resetStreaming);
  const setStreaming = useChatExplainStore((s) => s.setStreaming);
  const isStreaming = useChatExplainStore((s) => s.isStreaming);
  const streamingText = useChatExplainStore((s) => s.streamingText);

  function updateStickFromScroll() {
    const el = scrollAreaRef.current;
    if (!el) return;
    const threshold = 96;
    const dist = el.scrollHeight - el.scrollTop - el.clientHeight;
    const near = dist < threshold;
    stickToBottomRef.current = near;
    if (near) setShowJumpToBottom(false);
  }

  useEffect(() => {
    const el = scrollAreaRef.current;
    if (!el) return;
    const onScroll = () => updateStickFromScroll();
    el.addEventListener("scroll", onScroll, { passive: true });
    return () => el.removeEventListener("scroll", onScroll);
  }, [conversationId]);

  useEffect(() => {
    stickToBottomRef.current = true;
    const resetTimer = setTimeout(() => setShowJumpToBottom(false), 0);
    return () => clearTimeout(resetTimer);
  }, [conversationId]);

  useEffect(() => {
    const el = scrollAreaRef.current;
    if (!el) return;
    if (stickToBottomRef.current) {
      requestAnimationFrame(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }));
    } else {
      setShowJumpToBottom(true);
    }
  }, [messages, conversationId, streamingText, optimisticUserContent]);

  const modelsErrorMessage = useMemo(() => {
    if (!modelsError) return null;
    const e = modelsQueryError;
    if (e instanceof ApiError) {
      if (e.status === 401 || e.status === 403) return t("modelsLoadErrorAuth");
      if (e.status === 502 || e.status === 503 || e.status === 504) {
        return t("modelsLoadErrorGateway");
      }
      if (e.status === 0 && e.meta?.kind === "network") return t("modelsLoadErrorGateway");
      return getSafeApiErrorMessage(e);
    }
    return t("modelsLoadError");
  }, [modelsError, modelsQueryError, t]);

  const presetLabelOpts = useMemo(
    () => ({
      systemSuffix: t("presetSystem"),
      recommendedDefault: t("presetRecommendedDefault"),
      defaultConfiguration: t("presetDefaultConfiguration"),
    }),
    [t],
  );

  // R1: /runtime-config/validate remains draft-only; do not use it for steady-state rendering.

  /** Cancel in-flight chat job when switching project, conversation, or unmounting. */
  useEffect(() => {
    return () => {
      if (activeJobIdRef.current) {
        void cancelChatJob(activeJobIdRef.current).catch(() => {});
      }
    };
  }, []);

  useEffect(() => {
    const jid = activeJobIdRef.current;
    if (jid) {
      void cancelChatJob(jid).catch(() => {});
      activeJobIdRef.current = null;
      abortRef.current?.abort();
      resetStreaming();
      setStreaming(false);
    }
    const resetUiTimer = setTimeout(() => {
      setOptimisticUserContent(null);
      setAssistantPhase(null);
    }, 0);
    return () => clearTimeout(resetUiTimer);
  }, [conversationId, projectId, resetStreaming, setStreaming]);

  /** Load persisted draft when opening a conversation. */
  useEffect(() => {
    const t = setTimeout(() => {
      setEditingUserMessageId(null);
      setEditBody("");
      setEditError(null);
    }, 0);
    return () => clearTimeout(t);
  }, [conversationId]);

  useEffect(() => {
    if (
      editingUserMessageId &&
      lastUserMessageId &&
      editingUserMessageId !== lastUserMessageId
    ) {
      const t = setTimeout(() => {
        setEditingUserMessageId(null);
        setEditBody("");
        setEditError(null);
      }, 0);
      return () => clearTimeout(t);
    }
  }, [editingUserMessageId, lastUserMessageId]);

  useEffect(() => {
    if (!conversationId) {
      const t = setTimeout(() => setInput(""), 0);
      return () => clearTimeout(t);
    }
    let cancelled = false;
    const clearComposerTimer = setTimeout(() => setInput(""), 0);
    void apiFetch<ConversationDraftDto>(apiProductPath(`/conversations/${conversationId}/draft`))
      .then((d) => {
        if (cancelled) return;
        scheduleHydrateComposerFromDraft(d.content ?? "", () => cancelled, setInput);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      clearTimeout(clearComposerTimer);
    };
  }, [conversationId]);

  /** Debounced draft save. */
  useEffect(() => {
    if (!conversationId) return;
    if (draftSaveTimer.current) clearTimeout(draftSaveTimer.current);
    draftSaveTimer.current = setTimeout(() => {
      void apiFetch<ConversationDraftDto>(apiProductPath(`/conversations/${conversationId}/draft`), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: input }),
      }).catch(() => {});
    }, 600);
    return () => {
      if (draftSaveTimer.current) clearTimeout(draftSaveTimer.current);
    };
  }, [conversationId, input]);

  const runChatJob = useCallback(
    async (accepted: LabJobAcceptedDto, signal: AbortSignal) => {
      resetStreaming();
      setLastDone(null);
      setStreaming(true);
      setAssistantPhase("processing");
      activeJobIdRef.current = accepted.jobId;
      let streamingSeen = false;
      try {
        const terminal = await followLabJob(
          accepted,
          (s) => {
            const streamed = s.result?.streamedAnswer;
            if (typeof streamed === "string" && streamed.length > 0) {
              if (!streamingSeen) {
                streamingSeen = true;
                setAssistantPhase("receiving");
              }
              setStreamingText(streamed);
            }
          },
          { signal, throwOnFailed: false },
        );
        activeJobIdRef.current = null;
        resetStreaming();
        setStreaming(false);
        const refreshed = await refetchMessages();

        if (terminal.status === "FAILED") {
          setLastDone(null);
          setAssistantPhase("failed");
          const sanitized = sanitizePlainErrorTextForUi(terminal.errorMessage, 280);
          const hint = resolveChatJobFailureUserHint({
            task: terminal,
            errorMessageSanitized: sanitized,
            t,
          });
          setSendError(hint);
          useTraceStore.getState().addTraceEvent({
            section: "chat",
            action: "assistant_failed",
            message: t("traceAssistantFailed"),
            status: "error",
            metadata: {
              jobId: accepted.jobId,
              ...(terminal.failureCode ? { failureCode: terminal.failureCode } : {}),
            },
          });
        } else {
          setAssistantPhase(null);
          setSendError(null);
          const list = refreshed.data ?? [];
          const lastAssistant = [...list]
            .reverse()
            .find((x) => x.role === "ASSISTANT" && x.status === "DONE");
          if (lastAssistant) {
            setLastDone(streamDonePayloadFromAssistantMessage(lastAssistant));
          } else {
            setLastDone(null);
          }
          useTraceStore.getState().addTraceEvent({
            section: "chat",
            action: "assistant_response_received",
            message: t("traceAssistantCompleted"),
            status: "success",
            metadata: { jobId: accepted.jobId },
          });
        }
      } catch (e) {
        activeJobIdRef.current = null;
        setStreaming(false);
        resetStreaming();
        if (e instanceof DOMException && e.name === "AbortError") {
          setAssistantPhase(null);
          return;
        }
        setAssistantPhase("failed");
        setSendError(getSafeApiErrorMessage(e));
        useTraceStore.getState().addTraceEvent({
          section: "chat",
          action: "assistant_failed",
          message: t("traceAssistantFailed"),
          status: "error",
          metadata: { jobId: accepted.jobId },
        });
      } finally {
        setStreaming(false);
      }
    },
    [refetchMessages, resetStreaming, setLastDone, setStreaming, setStreamingText, t],
  );

  const send = useCallback(async () => {
    if (!input.trim() || isSending || isStreaming) return;
    if (runtimeBlockingMessage) {
      setSendError(runtimeBlockingMessage);
      return;
    }
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const signal = abortRef.current.signal;
    setEditError(null);
    const text = input.trim();
    useTraceStore.getState().addTraceEvent({
      section: "chat",
      action: "message_submitted",
      message: t("traceMessageSubmitted"),
      status: "info",
    });
    setOptimisticUserContent(text);
    setInput("");
    setAssistantPhase("sending");
    const body: PostMessageBody = {
      content: text,
      llmModel: llmModelChoice.trim() ? llmModelChoice.trim() : null,
    };
    let targetConversationId = conversationId;
    setIsSending(true);
    setSendError(null);
    try {
      if (!targetConversationId) {
        const created = await createConv.mutateAsync(undefined);
        targetConversationId = created.id;
        selectConversation(created.id);
      }
      if (!targetConversationId) return;

      // R1: rely on runtime-state as the authoritative validation source.
      const rs = await apiFetch<{
        isValid?: boolean;
        blockingIssues?: { message?: string | null }[];
        validation: { valid: boolean; supported: boolean; errors: { message: string }[] };
      }>(
        apiProductPath(`/conversations/${targetConversationId}/runtime-state`),
        { signal },
      );
      const blocking = firstRuntimeBlockingMessage(rs);
      if (blocking) {
        const msg = blocking;
        setSendError(msg);
        setInput(text);
        setOptimisticUserContent(null);
        setAssistantPhase(null);
        return;
      }

      const accepted = await apiFetch<LabJobAcceptedDto>(
        apiProductPath(`/conversations/${targetConversationId}/messages`),
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
          signal,
        },
      );
      setAssistantPhase("contacting");
      useTraceStore.getState().addTraceEvent({
        section: "chat",
        action: "assistant_processing_started",
        message: t("traceAssistantStarted"),
        status: "in_progress",
        metadata: { jobId: accepted.jobId },
      });
      try {
        await refetchMessages();
      } catch (refetchErr) {
        setSendError(getSafeApiErrorMessage(refetchErr));
      }
      await runChatJob(accepted, signal);
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        setInput(text);
        setOptimisticUserContent(null);
        setAssistantPhase(null);
        return;
      }
      setAssistantPhase("failed");
      setInput(text);
      setSendError(getSafeApiErrorMessage(e));
      useTraceStore.getState().addTraceEvent({
        section: "chat",
        action: "message_submit_failed",
        message: t("traceMessageSubmitFailed"),
        status: "error",
      });
    } finally {
      setIsSending(false);
    }
  }, [
    conversationId,
    createConv,
    input,
    isSending,
    isStreaming,
    llmModelChoice,
    refetchMessages,
    runChatJob,
    runtimeBlockingMessage,
    selectConversation,
    t,
  ]);

  const retryAssistant = useCallback(
    async (assistantMessageId: string) => {
      if (!conversationId || isSending) return;
      abortRef.current?.abort();
      abortRef.current = new AbortController();
      const signal = abortRef.current.signal;
      setIsSending(true);
      setSendError(null);
      setAssistantPhase("sending");
      try {
        const accepted = await apiFetch<LabJobAcceptedDto>(
          apiProductPath(`/conversations/${conversationId}/messages/${assistantMessageId}/retry`),
          { method: "POST", signal },
        );
        useTraceStore.getState().addTraceEvent({
          section: "chat",
          action: "assistant_processing_started",
          message: t("traceAssistantStarted"),
          status: "in_progress",
          metadata: { jobId: accepted.jobId },
        });
        try {
          await refetchMessages();
        } catch (refetchErr) {
          setSendError(getSafeApiErrorMessage(refetchErr));
        }
        await runChatJob(accepted, signal);
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") {
          setAssistantPhase(null);
          return;
        }
        setAssistantPhase("failed");
        setSendError(getSafeApiErrorMessage(e));
        useTraceStore.getState().addTraceEvent({
          section: "chat",
          action: "message_submit_failed",
          message: t("traceMessageSubmitFailed"),
          status: "error",
        });
      } finally {
        setIsSending(false);
      }
    },
    [conversationId, isSending, refetchMessages, runChatJob, t],
  );

  const saveUserEditAndRegenerate = useCallback(async () => {
    if (!conversationId || !editingUserMessageId || !editBody.trim() || isSending) return;
    const userMsgId = editingUserMessageId;
    const text = editBody.trim();
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const signal = abortRef.current.signal;
    setEditError(null);
    setSendError(null);
    setIsSending(true);
    try {
      const patchBody: PatchUserMessageBody = { content: text };
      await apiFetch<void>(apiProductPath(`/conversations/${conversationId}/messages/${userMsgId}`), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(patchBody),
        signal,
      });
      const postBody: PostMessageBody = {
        content: text,
        llmModel: llmModelChoice.trim() ? llmModelChoice.trim() : null,
        continueAfterUserMessageId: userMsgId,
      };

      const rs = await apiFetch<{
        isValid?: boolean;
        blockingIssues?: { message?: string | null }[];
        validation: { valid: boolean; supported: boolean; errors: { message: string }[] };
      }>(
        apiProductPath(`/conversations/${conversationId}/runtime-state`),
        { signal },
      );
      const blocking = firstRuntimeBlockingMessage(rs);
      if (blocking) {
        setSendError(blocking);
        return;
      }

      const accepted = await apiFetch<LabJobAcceptedDto>(
        apiProductPath(`/conversations/${conversationId}/messages`),
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(postBody),
          signal,
        },
      );
      setEditingUserMessageId(null);
      setEditBody("");
      try {
        await refetchMessages();
      } catch (refetchErr) {
        setSendError(getSafeApiErrorMessage(refetchErr));
      }
      await runChatJob(accepted, signal);
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        return;
      }
      setEditError(t("editError"));
      setSendError(getSafeApiErrorMessage(e));
    } finally {
      setIsSending(false);
    }
  }, [
    conversationId,
    editBody,
    editingUserMessageId,
    isSending,
    llmModelChoice,
    refetchMessages,
    runChatJob,
    t,
  ]);

  const stop = useCallback(() => {
    abortRef.current?.abort();
    const jid = activeJobIdRef.current;
    if (jid) {
      void cancelChatJob(jid).catch(() => {});
      activeJobIdRef.current = null;
    }
    resetStreaming();
    setStreaming(false);
    setAssistantPhase(null);
    void refetchMessages();
  }, [refetchMessages, resetStreaming, setStreaming]);

  const optimisticVisible =
    Boolean(optimisticUserContent?.trim()) && !optimisticConsumed(messages, optimisticUserContent);

  const assistantPipelineLabel = useMemo(() => {
    if (assistantPhase === "failed" || sendError) {
      return sendError ?? t("assistantPhaseFailed");
    }
    switch (assistantPhase) {
      case "sending":
        return t("assistantPhaseSending");
      case "contacting":
        return t("assistantPhaseContacting");
      case "processing":
        return t("assistantPhaseProcessing");
      case "receiving":
        return t("assistantPhaseReceiving");
      default:
        return "";
    }
  }, [assistantPhase, sendError, t]);

  const showAssistantPipelineRow =
    !(streamingText?.trim()) &&
    Boolean(assistantPipelineLabel) &&
    (isSending || isStreaming || assistantPhase !== null);

  const assistantPipelineTraceStatus =
    assistantPhase === "failed" || sendError ? "error" : "in_progress";

  const onPresetChange = useCallback(
    (value: string) => {
      if (!conversationId) return;
      const next = value.trim();
      if (!next) {
        patchConv.mutate({ conversationId, body: { clearPreset: true } });
        return;
      }
      const exp = experimentalPresets?.find((p) => p.productPresetId === next);
      if (exp && !exp.chatSelectable) {
        // Disabled experimental presets must be visible but never persistable in Chat.
        return;
      }
      patchConv.mutate({ conversationId, body: { presetId: next } });
    },
    [conversationId, patchConv, experimentalPresets],
  );

  const applyLlmModelChoice = useCallback(
    (v: string) => {
      if (!conversationId) return;
      setLlmModelChoiceDraft({ conversationId, value: v });
      if (!v.trim()) {
        patchConv.mutate(
          { conversationId, body: { clearLlmModel: true } },
          { onError: () => setLlmModelChoiceDraft({ conversationId, value: activeConv?.llmModel ?? "" }) },
        );
        return;
      }
      patchConv.mutate(
        { conversationId, body: { llmModel: v.trim() } },
        { onError: () => setLlmModelChoiceDraft({ conversationId, value: activeConv?.llmModel ?? "" }) },
      );
    },
    [activeConv?.llmModel, conversationId, patchConv],
  );

  const applyClassifierModelChoice = useCallback(
    (v: string) => {
      if (!conversationId) return;
      setClassifierModelChoiceDraft({ conversationId, value: v });
      if (!v.trim()) {
        patchConv.mutate(
          { conversationId, body: { clearClassifierModelId: true } },
          {
            onError: () =>
              setClassifierModelChoiceDraft({ conversationId, value: activeConv?.classifierModelId ?? "" }),
          },
        );
        return;
      }
      patchConv.mutate(
        { conversationId, body: { classifierModelId: v.trim() } },
        {
          onError: () =>
            setClassifierModelChoiceDraft({ conversationId, value: activeConv?.classifierModelId ?? "" }),
        },
      );
    },
    [activeConv?.classifierModelId, conversationId, patchConv],
  );

  const handleChatDocumentUpload = useCallback(
    async (files: FileList | null) => {
      if (!files?.length || !conversationId || !projectId || !activeConv) return;
      setUploadError(null);
      setUploadNotice(null);
      // Keep previous items; allow multi-batch uploads.
      let merged = [...(activeConv.documentFilter ?? [])];
      for (const file of Array.from(files)) {
        const id =
          typeof crypto !== "undefined" && "randomUUID" in crypto
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random()}-${file.name}`;
        setUploadItems((prev) => [{ id, fileName: file.name, phase: "uploading" as const, file }, ...prev].slice(0, 20));
        try {
          const doc = await uploadDoc.mutateAsync(file);
          await refetchProjectDocuments();
          setUploadItems((prev) =>
            prev.map((x) =>
              x.id === id
                ? { ...x, phase: doc.status === "READY" ? "ready" : doc.status === "ERROR" ? "error" : "ingesting", docId: doc.id, chunkCount: doc.chunkCount ?? null, errorMessage: doc.errorMessage ?? null }
                : x,
            ),
          );
          let terminalStatus: ProjectDocumentDto["status"] = doc.status;
          if (doc.status !== "READY" && doc.status !== "ERROR") {
            const started = Date.now();
            while (true) {
              const tick = await apiFetch<ProjectDocumentDto>(apiProductPath(`/documents/${doc.id}/status`));
              setUploadItems((prev) =>
                prev.map((x) =>
                  x.docId === doc.id
                    ? {
                        ...x,
                        phase: tick.status === "READY" ? "ready" : tick.status === "ERROR" ? "error" : "ingesting",
                        chunkCount: tick.chunkCount ?? null,
                        errorMessage: tick.errorMessage ?? null,
                      }
                    : x,
                ),
              );
              terminalStatus = tick.status;
              if (tick.status === "READY" || tick.status === "ERROR") break;
              if (Date.now() - started > 5 * 60_000) {
                setUploadItems((prev) =>
                  prev.map((x) => (x.docId === doc.id ? { ...x, phase: "stalled" } : x)),
                );
                terminalStatus = "INGESTING";
                break;
              }
              await new Promise<void>((r) => setTimeout(r, 1500));
            }
          }
          await refetchProjectDocuments();
          if (terminalStatus === "READY") {
            if (merged.length > 0) {
              merged = Array.from(new Set([...merged, doc.id]));
              patchConv.mutate({ conversationId, body: { documentFilter: merged } });
            } else {
              setUploadNotice(t("documentsUploadAddedToProjectHint"));
            }
          } else if (terminalStatus === "INGESTING") {
            setUploadNotice(t("documentsUploadProcessingHint"));
          }
        } catch (e) {
          const msg = getSafeApiErrorMessage(e);
          setUploadError(msg);
          setUploadItems((prev) =>
            prev.map((x) =>
              x.id === id ? { ...x, phase: "error", errorMessage: msg } : x,
            ),
          );
          continue;
        }
      }
    },
    [conversationId, projectId, activeConv, uploadDoc, refetchProjectDocuments, patchConv, t],
  );

  const retryUploadItem = useCallback(
    async (id: string) => {
      if (!conversationId) return;
      const item = uploadItems.find((x) => x.id === id);
      if (!item?.docId) return;
      setUploadError(null);
      setUploadNotice(null);
      setUploadItems((prev) =>
        prev.map((x) => (x.id === id ? { ...x, phase: "ingesting", errorMessage: null } : x)),
      );
      try {
        await apiFetch<ProjectDocumentDto>(apiProductPath(`/documents/${item.docId}/retry-ingest`), { method: "POST" });
        const started = Date.now();
        while (true) {
          const tick = await apiFetch<ProjectDocumentDto>(apiProductPath(`/documents/${item.docId}/status`));
          setUploadItems((prev) =>
            prev.map((x) =>
              x.id === id
                ? {
                    ...x,
                    phase: tick.status === "READY" ? "ready" : tick.status === "ERROR" ? "error" : "ingesting",
                    chunkCount: tick.chunkCount ?? null,
                    errorMessage: tick.errorMessage ?? null,
                  }
                : x,
            ),
          );
          if (tick.status === "READY" || tick.status === "ERROR") break;
          if (Date.now() - started > 5 * 60_000) {
            setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, phase: "stalled" } : x)));
            break;
          }
          await new Promise<void>((r) => setTimeout(r, 1500));
        }
        await refetchProjectDocuments();
      } catch (e) {
        const msg = getSafeApiErrorMessage(e);
        setUploadError(msg);
        setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, phase: "error", errorMessage: msg } : x)));
      }
    },
    [conversationId, uploadItems, refetchProjectDocuments],
  );

  const checkUploadItem = useCallback(
    async (id: string) => {
      const item = uploadItems.find((x) => x.id === id);
      if (!item?.docId) return;
      try {
        const tick = await apiFetch<ProjectDocumentDto>(apiProductPath(`/documents/${item.docId}/status`));
        setUploadItems((prev) =>
          prev.map((x) =>
            x.id === id
              ? {
                  ...x,
                  phase: tick.status === "READY" ? "ready" : tick.status === "ERROR" ? "error" : "ingesting",
                  chunkCount: tick.chunkCount ?? null,
                  errorMessage: tick.errorMessage ?? null,
                }
              : x,
          ),
        );
      } catch (e) {
        const msg = getSafeApiErrorMessage(e);
        setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, phase: "error", errorMessage: msg } : x)));
      }
      await refetchProjectDocuments();
    },
    [uploadItems, refetchProjectDocuments],
  );

  const onLimitDocsChange = useCallback(
    (checked: boolean) => {
      if (!conversationId) return;
      setLimitDocsNoticeRecord({ conversationId, message: null });
      if (!checked) {
        patchConv.mutate({ conversationId, body: { documentFilter: [] } });
        return;
      }
      // Prefer already-fetched docs so the checkbox becomes controlled immediately (no "revert" while awaiting refetch).
      if (readyDocIds.length === 0) {
        setLimitDocsNoticeRecord({ conversationId, message: t("limitDocumentsNoReadyHint") });
        return;
      }
      patchConv.mutate({ conversationId, body: { documentFilter: readyDocIds } });
    },
    [conversationId, patchConv, readyDocIds, t],
  );

  const onDocToggle = (documentId: string, checked: boolean) => {
    if (!conversationId || !activeConv) return;
    const current = [...(activeConv.documentFilter ?? [])];
    if (checked) {
      const next =
        current.length > 0 ? Array.from(new Set([...current, documentId])) : [documentId];
      patchConv.mutate({ conversationId, body: { documentFilter: next } });
      return;
    }
    if (current.length === 0) return;
    const next = current.filter((id) => id !== documentId);
    if (next.length === 0) {
      patchConv.mutate({ conversationId, body: { documentFilter: [] } });
      return;
    }
    patchConv.mutate({ conversationId, body: { documentFilter: next } });
  };

  const staleSelectedIds = useMemo(() => {
    if (!limitDocs) return [];
    const eligible = new Set((docs ?? []).map((d) => d.id));
    return (selectedDocIds ?? []).filter((id) => !eligible.has(id));
  }, [docs, limitDocs, selectedDocIds]);

  const cleanSelection = useCallback(() => {
    if (!conversationId) return;
    const eligibleReady = (docs ?? []).filter((d) => d.status === "READY").map((d) => d.id);
    patchConv.mutate({ conversationId, body: { documentFilter: eligibleReady } });
  }, [conversationId, docs, patchConv]);

  useEffect(() => {
    useChatConfigurationPanelStore.getState().hydrateFromStorage();
  }, []);

  const runtimeStateLoading = runtimeStateQuery.isLoading || runtimeStateQuery.isFetching;
  const runtimeStateError = runtimeStateQuery.isError ? getSafeApiErrorMessage(runtimeStateQuery.error) : null;

  const refreshRuntimeState = useCallback(() => {
    void runtimeStateQuery.refetch();
  }, [runtimeStateQuery]);

  useEffect(() => {
    if (!projectId) {
      useChatToolbarStore.getState().setApi(null);
      return;
    }
    useChatToolbarStore.getState().setApi({
      projectId,
      conversationId,
      openDeleteForActiveConversation: () => {
        if (!conversationId) return;
        setDeleteDialogTarget({ id: conversationId, title: activeConv?.title ?? "" });
      },
      openMoveDialog: () => setMoveDialogOpen(true),
      openDocumentsSheet: () => setDocsSheetOpen(true),
      onAddDocuments: handleChatDocumentUpload,
      llmModelChoice,
      setLlmModelChoice: applyLlmModelChoice,
      classifierModelChoice,
      setClassifierModelChoice: applyClassifierModelChoice,
      modelsCatalog,
      modelsError,
      modelsErrorMessage: modelsErrorMessage ?? "",
      presetSelectValue,
      onPresetChange,
      presets,
      presetsError,
      presetsLoading,
      experimentalPresets: experimentalPresets ?? [],
      experimentalPresetsLoading,
      experimentalPresetsError,
      presetSelectDisabled,
      syntheticPresetOptionNeeded,
      presetLabelOpts,
      limitDocs,
      onLimitDocsChange,
      limitDocsDisabled,
      limitDocsToggleNotice: limitDocsToggleNoticeEffective,
      patchConvPending: patchConv.isPending,
      uploadPending: uploadDoc.isPending,
      uploadError,
      uploadNotice,
      runtimeOverride: runtimeState?.runtimeOverride ?? {},
      saveRuntimeOverride: (next) => {
        if (!conversationId) return;
        patchConv.mutate({ conversationId, body: { runtimeOverride: next } });
      },
      clearRuntimeOverride: () => {
        if (!conversationId) return;
        patchConv.mutate({ conversationId, body: { clearRuntimeOverride: true } });
      },
      runtimeState,
      runtimeStateLoading,
      runtimeStateError,
      refreshRuntimeState,
    });
    return () => useChatToolbarStore.getState().setApi(null);
  }, [
    projectId,
    conversationId,
    activeConv?.title,
    llmModelChoice,
    applyLlmModelChoice,
    classifierModelChoice,
    applyClassifierModelChoice,
    modelsCatalog,
    modelsError,
    modelsErrorMessage,
    presetSelectValue,
    onPresetChange,
    presets,
    presetsError,
    presetsLoading,
    experimentalPresets,
    experimentalPresetsLoading,
    experimentalPresetsError,
    presetSelectDisabled,
    syntheticPresetOptionNeeded,
    presetLabelOpts,
    limitDocs,
    onLimitDocsChange,
    limitDocsDisabled,
    limitDocsToggleNoticeEffective,
    patchConv.isPending,
    handleChatDocumentUpload,
    uploadDoc.isPending,
    uploadError,
    uploadNotice,
    runtimeState,
    runtimeStateLoading,
    runtimeStateError,
    refreshRuntimeState,
    patchConv,
  ]);

  if (!projectId) {
    return (
      <div className="flex flex-col gap-3 text-muted-foreground text-sm">
        <p>{t("noActiveProject")}</p>
        <p>
          <Link href="/projects" className="text-primary underline underline-offset-4">
            {t("goToProjects")}
          </Link>
        </p>
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100dvh-7rem)] min-h-[420px] flex-col gap-2 md:flex-row md:gap-3">
      {convListCollapsed ? (
        <div className="flex w-full shrink-0 flex-col items-stretch gap-2 border-border border-b pb-2 md:w-auto md:border-b-0 md:border-r md:pb-0 md:pr-2">
          <Button
            type="button"
            variant="outline"
            size="icon-sm"
            className="shrink-0 self-start"
            aria-expanded={false}
            aria-label={t("sidebarExpand")}
            onClick={() => persistConvListCollapsed(false)}
          >
            <PanelLeftOpen className="size-4" />
          </Button>
          <p className="text-muted-foreground hidden text-xs md:block md:max-w-[7rem]">{t("sidebarCollapsedHint")}</p>
        </div>
      ) : (
        <aside
          data-testid="chat-conversation-sidebar"
          className="flex w-full shrink-0 flex-col gap-2 border-border border-b pb-2 md:w-44 md:max-w-[13rem] md:border-b-0 md:border-r md:pb-0 md:pr-2 lg:w-48 lg:max-w-[14rem]"
        >
          <div className="flex items-center justify-end">
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-expanded={true}
              aria-label={t("sidebarCollapse")}
              onClick={() => persistConvListCollapsed(true)}
            >
              <PanelLeftClose className="size-4" />
            </Button>
          </div>
          <Button
            type="button"
            size="sm"
            className="w-full"
            data-testid="chat-new-conversation"
            disabled={createConv.isPending}
            onClick={() => setNewConvWizardOpen(true)}
          >
            {t("newConversation")}
          </Button>
          <div
            data-testid="conversation-list"
            className="flex max-h-48 flex-col gap-1 overflow-y-auto md:max-h-none md:flex-1"
          >
            {convs?.map((c) => (
              <div key={c.id} className="flex items-stretch gap-1">
                <Button
                  type="button"
                  data-testid={`conversation-item-${c.id}`}
                  aria-current={conversationId === c.id ? "true" : undefined}
                  variant={conversationId === c.id ? "secondary" : "ghost"}
                  size="sm"
                  className="h-auto min-w-0 flex-1 justify-start py-2 text-left"
                  onClick={() => selectConversation(c.id)}
                >
                  <span className="line-clamp-2 text-xs">{c.title}</span>
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="shrink-0 text-muted-foreground hover:text-destructive"
                  aria-label={t("deleteConversationTriggerAria", {
                    title: (c.title ?? "").trim() || t("deleteConversationUntitled"),
                  })}
                  onClick={() =>
                    setDeleteDialogTarget({ id: c.id, title: c.title ?? "" })
                  }
                >
                  <Trash2 className="size-4" aria-hidden />
                </Button>
              </div>
            ))}
          </div>
        </aside>
      )}
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden md:flex-row md:gap-3">
        <div
          data-testid="chat-readable-column"
          className="flex w-full min-h-0 min-w-0 flex-1 flex-col gap-3 px-2 sm:px-3 md:px-5"
        >
        {conversationId && active ? (
          <header className="flex flex-wrap items-end gap-3 gap-y-2 border-border border-b pb-3">
            <div className="flex min-w-0 flex-1 flex-wrap items-end gap-3">
              <div className="flex min-w-[10rem] items-center gap-2">
                <ProjectVisual
                  iconKey={currentProject?.iconKey}
                  colorHex={currentProject?.colorHex}
                  dotClassName="inline-block size-3 shrink-0 rounded-full border border-border"
                />
                <span className="truncate font-medium text-sm">{active.name}</span>
              </div>
              <div className="flex min-w-[min(100%,14rem)] flex-1 flex-col gap-1">
                <Label htmlFor="chat-title" className="text-muted-foreground text-xs">
                  {t("chatTitleLabel")}
                </Label>
                <Input
                  id="chat-title"
                  value={titleDraft}
                  onChange={(e) => setTitleDraft(e.target.value)}
                  onBlur={() => {
                    if (!conversationId || !activeConv) return;
                    const next = titleDraft.trim();
                    if (next === (activeConv.title ?? "").trim()) return;
                    patchConv.mutate(
                      { conversationId, body: { title: next } },
                      {
                        onError: () => setTitleDraft(activeConv.title ?? ""),
                      },
                    );
                  }}
                  disabled={patchConv.isPending}
                  className="h-9"
                />
              </div>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-1 pb-0.5 sm:flex-row sm:items-center">
              <HelpPopover
                triggerAriaLabel={tHelp("chatControlsTriggerLabel")}
                title={tHelp("chatControlsTitle")}
                message={tHelp("chatControlsMessage")}
                details={tHelp("chatControlsDetails")}
              />
            </div>
          </header>
        ) : null}
        {conversationId && active ? (
          <ChatConversationDocumentsSheet
            open={docsSheetOpen}
            onOpenChange={(next) => {
              setDocsSheetOpen(next);
              if (!next) {
                setUploadError(null);
                setUploadNotice(null);
              }
            }}
            projectName={active.name}
            docs={docs}
            limitDocs={limitDocs}
            selectedDocIds={selectedDocIds}
            patchPending={patchConv.isPending}
            uploadPending={uploadDoc.isPending}
            uploadError={uploadError}
            uploadNotice={uploadNotice}
            uploadItems={uploadItems}
            onDocToggle={onDocToggle}
            onUploadFiles={handleChatDocumentUpload}
            onRetryUploadItem={retryUploadItem}
            onCheckUploadItem={checkUploadItem}
            staleSelectionWarning={
              staleSelectedIds.length > 0
                ? "Some selected documents are no longer available."
                : null
            }
            onCleanSelection={staleSelectedIds.length > 0 ? cleanSelection : undefined}
          />
        ) : null}
        <div
          ref={scrollAreaRef}
          data-testid="chat-thread-dropzone"
          className="relative min-h-0 flex-1 space-y-3 overflow-y-auto rounded-lg border bg-card/30 p-3"
          onDragOver={(e) => {
            // Allow dropping files anywhere in the chat thread area.
            e.preventDefault();
            if (!conversationId || !projectId) return;
            setChatDropActive(true);
          }}
          onDragLeave={() => setChatDropActive(false)}
          onDrop={(e) => {
            e.preventDefault();
            setChatDropActive(false);
            if (!conversationId || !projectId) return;
            const files = e.dataTransfer.files;
            if (!files || files.length === 0) return;
            setDocsSheetOpen(true);
            void handleChatDocumentUpload(files);
          }}
        >
          {chatDropActive ? (
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-2 z-10 rounded-lg border-2 border-dashed border-primary bg-primary/5"
            />
          ) : null}
          {showJumpToBottom ? (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="absolute right-4 bottom-4 z-10 shadow-md"
              onClick={() => {
                stickToBottomRef.current = true;
                setShowJumpToBottom(false);
                requestAnimationFrame(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }));
              }}
            >
              <ChevronDown className="mr-1 size-4 shrink-0" aria-hidden />
              {t("jumpToBottom")}
            </Button>
          ) : null}
          {!conversationId && <p className="text-muted-foreground text-sm">{t("pickConversation")}</p>}
          {conversationNotFound && (
            <div className="flex flex-col gap-2 rounded-md border border-destructive/30 bg-destructive/5 p-3">
              <p className="text-sm">{t("conversationNotFound")}</p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setConversationId(null);
                    router.push(buildProjectScopedChatHref(projectId, null));
                  }}
                >
                  {t("backToConversations")}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={() => {
                    setConversationId(null);
                    router.push(buildProjectScopedChatHref(projectId, null));
                  }}
                >
                  {t("startNewChat")}
                </Button>
              </div>
            </div>
          )}
          {messages?.map((m) => (
            <div
              key={m.id}
              className={
                m.role === "USER"
                  ? "ml-auto max-w-[85%] rounded-lg bg-primary px-3 py-2 text-primary-foreground text-sm leading-relaxed"
                  : cn(
                      "mr-auto max-w-[85%] rounded-lg border px-3 py-2 text-sm leading-relaxed",
                      isAssistantClarificationTurn(m)
                        ? "border-amber-500/55 bg-amber-500/10"
                        : "bg-background",
                    )
              }
            >
              {m.role === "USER" &&
              editingUserMessageId === m.id &&
              m.id === lastUserMessageId ? (
                <div className="flex flex-col gap-2">
                  <Textarea
                    value={editBody}
                    onChange={(e) => setEditBody(e.target.value)}
                    rows={4}
                    className="resize-none border-border bg-card text-card-foreground placeholder:text-muted-foreground dark:bg-card dark:text-card-foreground"
                    disabled={isStreaming}
                  />
                  <div className="flex flex-wrap gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      className="h-8"
                      disabled={isStreaming || isSending || runtimeStateInvalid || !editBody.trim()}
                      onClick={() => void saveUserEditAndRegenerate()}
                    >
                      {t("saveAndRegenerate")}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="h-8 text-primary-foreground hover:bg-primary-foreground/10"
                      disabled={isStreaming}
                      onClick={() => {
                        setEditingUserMessageId(null);
                        setEditBody("");
                        setEditError(null);
                      }}
                    >
                      {t("cancelEdit")}
                    </Button>
                  </div>
                </div>
              ) : (
                <>
                  {isAssistantClarificationTurn(m) ? (
                    <p
                      className="text-muted-foreground mb-1 text-[11px] font-semibold tracking-wide uppercase"
                      data-testid="chat-clarification-label"
                    >
                      {t("clarificationQuestionLabel")}
                    </p>
                  ) : null}
                  <p className="whitespace-pre-wrap break-words">{m.content}</p>
                  {m.role === "ASSISTANT" && Array.isArray(m.sources) && m.sources.length > 0 ? (
                    <div className="mt-2 border-border border-t pt-2">
                      <p className="text-muted-foreground text-[11px] font-medium">
                        Sources ({m.sources.length})
                      </p>
                      <ul className="mt-1 space-y-1 text-[11px]">
                        {m.sources.slice(0, 5).map((raw, idx) => {
                          const s = raw as Record<string, unknown>;
                          const file =
                            s.fileName ?? s.filename ?? s.documentName ?? s.documentId ?? `source-${idx + 1}`;
                          const excerpt = s.excerpt ?? s.text ?? s.content ?? null;
                          const score = s.score ?? s.distance ?? null;
                          return (
                            <li key={idx} className="rounded-sm bg-muted/20 px-2 py-1">
                              <div className="flex items-baseline justify-between gap-2">
                                <span className="truncate font-medium">{String(file)}</span>
                                {score != null ? (
                                  <span className="font-mono text-muted-foreground">{String(score)}</span>
                                ) : null}
                              </div>
                              {excerpt ? (
                                <p className="text-muted-foreground mt-0.5 line-clamp-3 whitespace-pre-wrap">
                                  {String(excerpt)}
                                </p>
                              ) : null}
                            </li>
                          );
                        })}
                      </ul>
                    </div>
                  ) : null}
                  {m.role === "USER" && m.id === lastUserMessageId && (
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      className="mt-2 h-7 text-xs"
                      disabled={isStreaming}
                      onClick={() => {
                        setEditingUserMessageId(m.id);
                        setEditBody(m.content);
                        setEditError(null);
                      }}
                    >
                      {t("editMessage")}
                    </Button>
                  )}
                  {m.role === "ASSISTANT" && isAssistantRetryable(m.status) && (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="mt-2 h-7 text-xs"
                      disabled={isStreaming || isSending}
                      onClick={() => void retryAssistant(m.id)}
                    >
                      {t("retryAssistant")}
                    </Button>
                  )}
                  {m.role === "ASSISTANT" && m.status && m.status !== "DONE" && (
                    <p className="text-muted-foreground mt-1 text-xs">[{m.status}]</p>
                  )}
                </>
              )}
            </div>
          ))}
          {optimisticVisible ? (
            <article
              aria-label={t("optimisticUserAria")}
              data-testid="chat-optimistic-user"
              className="ml-auto max-w-[85%] rounded-lg bg-primary px-3 py-2 text-primary-foreground text-sm leading-relaxed"
            >
              <p className="whitespace-pre-wrap break-words">{optimisticUserContent}</p>
            </article>
          ) : null}
          {showAssistantPipelineRow && assistantPipelineLabel ? (
            <div className="mr-auto max-w-[85%] w-full min-w-0">
              <InlineHelpStatus
                status={assistantPipelineTraceStatus}
                label={assistantPipelineLabel}
              />
            </div>
          ) : null}
          {isStreaming && streamingText && (
            <div className="mr-auto max-w-[85%] rounded-lg border border-dashed bg-muted/20 px-3 py-2 text-sm leading-relaxed">
              <p className="whitespace-pre-wrap break-words">{streamingText}</p>
            </div>
          )}
          <div ref={bottomRef} />
        </div>
        <div className="flex w-full min-w-0 flex-col gap-2">
          {editError && (
            <p className="text-destructive break-words text-xs" role="alert">
              {editError}
            </p>
          )}
          {sendError && (
            <p className="text-destructive break-words text-xs" role="alert">
              {sendError}
            </p>
          )}
          {patchConv.isError ? (
            <p className="text-destructive text-xs" role="alert">
              {getSafeApiErrorMessage(patchConv.error)}
            </p>
          ) : null}
          {conversationId && messages && messages.length === 0 ? (
            <p
              className="text-muted-foreground rounded-md border border-dashed bg-muted/30 px-3 py-2 text-xs"
              role="status"
            >
              {t("emptyConversationAdjustHint")}
            </p>
          ) : null}
          {runtimeState?.effectiveConfig && coerceBool(runtimeState.effectiveConfig.memoryEnabled) ? (
            <p className="text-muted-foreground text-xs" data-testid="chat-memory-badge" role="status">
              {t("memoryActiveBadge")}
            </p>
          ) : null}
          {runtimeBlockingMessage ? (
            <p
              className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-xs text-destructive"
              data-testid="chat-runtime-blocking-input-message"
              role="alert"
            >
              {runtimeBlockingMessage}
            </p>
          ) : null}
          {conversationId &&
          activeConv?.pendingClarification &&
          Object.keys(activeConv.pendingClarification).length > 0 ? (
            <div className="flex flex-wrap items-center gap-2 rounded-md border border-amber-500/35 bg-amber-500/10 px-3 py-2 text-xs">
              <span className="text-muted-foreground flex-1">{t("clarificationPendingBanner")}</span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-8 shrink-0"
                disabled={patchConv.isPending}
                onClick={() => {
                  if (!conversationId) return;
                  void patchConv.mutateAsync({
                    conversationId,
                    body: { clearPendingClarification: true },
                  });
                }}
              >
                {t("cancelClarification")}
              </Button>
            </div>
          ) : null}
          <Textarea
            data-testid="chat-message-composer"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={t("placeholder")}
            rows={3}
            className="resize-none"
            aria-busy={isSending || isStreaming}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey && !isSending && !isStreaming && !runtimeStateInvalid) {
                e.preventDefault();
                void send();
              }
            }}
            disabled={isSending || isStreaming || runtimeStateInvalid}
          />
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!conversationId || patchConv.isPending || uploadDoc.isPending}
              onClick={() => setDocsSheetOpen(true)}
            >
              {t("chatAddDocuments")}
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={!isStreaming} onClick={() => stop()}>
              {t("stop")}
            </Button>
            <Button
              type="button"
              size="sm"
              data-testid="chat-send-button"
              disabled={!input.trim() || isSending || isStreaming || runtimeStateInvalid}
              onClick={() => void send()}
            >
              {t("send")}
            </Button>
          </div>
        </div>
        </div>
        <ChatConfigurationSidePanel
          open={Boolean(conversationId && configPanelOpen)}
          onClose={() => setConfigPanelOpen(false)}
        />
        <MoveConversationDialog
          sourceProjectId={projectId}
          conversationId={conversationId}
          showTrigger={false}
          open={moveDialogOpen}
          onOpenChange={setMoveDialogOpen}
        />
        <DeleteConversationDialog
          open={Boolean(deleteDialogTarget)}
          onOpenChange={(next) => {
            if (!next) setDeleteDialogTarget(null);
          }}
          projectId={projectId}
          conversationId={deleteDialogTarget?.id}
          conversationTitle={deleteDialogTarget?.title ?? ""}
          onDeleted={() => {
            const deletedId = deleteDialogTarget?.id;
            if (deletedId && conversationId === deletedId && projectId) {
              router.push(buildProjectScopedChatHref(projectId, null));
              setConversationId(null);
            }
          }}
        />
        {projectId ? (
          <NewConversationDialog
            projectId={projectId}
            open={newConvWizardOpen}
            onOpenChange={setNewConvWizardOpen}
            onCreated={(c) => {
              selectConversation(c.id);
              void refetchMessages();
            }}
          />
        ) : null}
      </div>
    </div>
  );
}

export default function ChatPage() {
  // Next.js requires useSearchParams() to be under a Suspense boundary for the CSR bailout.
  return (
    <Suspense fallback={null}>
      <ChatPageInner />
    </Suspense>
  );
}
