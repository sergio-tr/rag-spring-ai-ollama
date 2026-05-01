"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Textarea } from "@/components/ui/textarea";
import { MoveConversationDialog } from "@/features/chat/components/MoveConversationDialog";
import { documentFiltersEqual } from "@/features/chat/lib/chat-document-filter-sync";
import {
  findPresetById,
  resolveConversationPresetSelectValue,
  resolvePresetSelectLabel,
} from "@/features/chat/lib/conversation-preset-ui";
import {
  useConversationMessages,
  useConversations,
  useCreateConversation,
  usePatchConversation,
} from "@/features/chat/hooks/use-conversations";
import { useModelsCatalog } from "@/features/chat/hooks/use-models-catalog";
import { useRagPresets } from "@/features/chat/hooks/use-rag-presets";
import { useProjectDocuments } from "@/features/documents/hooks/use-project-documents";
import { useProjectList } from "@/features/projects/hooks/use-projects";
import { ProjectVisual } from "@/features/projects/components/ProjectVisual";
import { ApiError, apiFetch, apiProductPath, getSafeApiErrorMessage } from "@/lib/api-client";
import { followLabJob } from "@/lib/lab-job-follow";
import { cn } from "@/lib/utils";
import { useRouter } from "@/navigation";
import { useAppStore } from "@/store/app.store";
import { useChatExplainStore } from "@/store/chat-explain.store";
import type {
  ConversationDraftDto,
  LabJobAcceptedDto,
  PatchUserMessageBody,
  PostMessageBody,
} from "@/types/api";
import { ChevronDown, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";

const CHAT_CONV_LIST_COLLAPSED_KEY = "chat-conv-list-collapsed";

async function cancelChatJob(jobId: string, signal?: AbortSignal): Promise<void> {
  await apiFetch<void>(apiProductPath(`/lab/jobs/${jobId}/cancel`), {
    method: "POST",
    signal,
  });
}

function isAssistantRetryable(status: string | null | undefined): boolean {
  return status === "ERROR" || status === "CANCELLED";
}

function ChatPageInner() {
  const t = useTranslations("Chat");
  const router = useRouter();
  const searchParams = useSearchParams();
  const active = useAppStore((s) => s.activeProject);
  const projectId = active?.id;
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
  /** Empty string = backend default model. */
  const [llmModelChoice, setLlmModelChoice] = useState("");
  const [presetSelectValue, setPresetSelectValue] = useState("");
  const [limitDocs, setLimitDocs] = useState(false);
  const [selectedDocIds, setSelectedDocIds] = useState<string[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const activeJobIdRef = useRef<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const stickToBottomRef = useRef(true);
  const [showJumpToBottom, setShowJumpToBottom] = useState(false);
  const [titleDraft, setTitleDraft] = useState("");
  const draftSaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingDocumentFilterRef = useRef<string[] | null>(null);
  /** POST + async chat job in flight (distinct from streaming preview state). */
  const [isSending, setIsSending] = useState(false);

  useEffect(() => {
    if (urlConversationId && urlConversationId !== conversationId) {
      const syncTimer = setTimeout(() => setConversationId(urlConversationId), 0);
      return () => clearTimeout(syncTimer);
    }
  }, [urlConversationId, conversationId]);

  const selectConversation = useCallback((nextId: string) => {
    setConversationId(nextId);
    router.push(`/chat?conversationId=${encodeURIComponent(nextId)}`);
  }, [router]);

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
  const { data: messages, refetch: refetchMessages } = useConversationMessages(conversationId ?? undefined);
  /** Only the latest user turn can be edited (matches backend truncate-from semantics). */
  const lastUserMessageId = useMemo(() => {
    if (!messages?.length) return null;
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].role === "USER") return messages[i].id;
    }
    return null;
  }, [messages]);
  const { data: docs, refetch: refetchProjectDocuments } = useProjectDocuments(projectId);
  const { data: projectListData } = useProjectList(0, 64);
  const currentProject = useMemo(
    () => projectListData?.items?.find((p) => p.id === projectId),
    [projectListData?.items, projectId],
  );
  const { data: modelsCatalog, isError: modelsError, error: modelsQueryError } = useModelsCatalog();
  const { data: presets, isError: presetsError } = useRagPresets();

  const activeConv = useMemo(
    () => (conversationId && convs ? convs.find((c) => c.id === conversationId) : undefined),
    [conversationId, convs],
  );
  const conversationNotFound = Boolean(conversationId && convs && !activeConv);

  /** Stable key for server-side document filter only (avoids tying preset PATCH churn to checkbox sync). */
  const documentFilterStableKey = useMemo(() => {
    if (!activeConv) return "";
    const sorted = JSON.stringify([...(activeConv.documentFilter ?? [])].sort());
    return `${activeConv.id}:${sorted}`;
  }, [activeConv]);

  const presetSyncKey = useMemo(() => {
    if (!activeConv) return "";
    return `${activeConv.id}:${activeConv.presetId ?? ""}:${activeConv.effectivePresetId ?? ""}`;
  }, [activeConv]);

  const syntheticPresetOptionNeeded = useMemo(() => {
    if (!presetSelectValue) return false;
    if (!presets?.length) return true;
    return !findPresetById(presets, presetSelectValue);
  }, [presetSelectValue, presets]);

  useEffect(() => {
    const syncTimer = setTimeout(() => setTitleDraft(activeConv?.title ?? ""), 0);
    return () => clearTimeout(syncTimer);
  }, [activeConv?.id, activeConv?.title]);

  useEffect(() => {
    if (!presetSyncKey || patchConv.isPending || !conversationId || !convs) return;
    const conv = convs.find((c) => c.id === conversationId);
    if (!conv) return;
    const next = resolveConversationPresetSelectValue(conv);
    const syncTimer = setTimeout(
      () => setPresetSelectValue((prev) => (prev === next ? prev : next)),
      0,
    );
    return () => clearTimeout(syncTimer);
  }, [presetSyncKey, patchConv.isPending, conversationId, convs]);

  useEffect(() => {
    if (!documentFilterStableKey || patchConv.isPending || !conversationId || !convs) return;
    const conv = convs.find((c) => c.id === conversationId);
    if (!conv) return;
    const serverFilter = conv.documentFilter ?? [];
    if (pendingDocumentFilterRef.current !== null) {
      if (!documentFiltersEqual(serverFilter, pendingDocumentFilterRef.current)) {
        return;
      }
      pendingDocumentFilterRef.current = null;
    }
    const hasFilter = serverFilter.length > 0;
    setLimitDocs((prev) => (prev === hasFilter ? prev : hasFilter));
    setSelectedDocIds((prev) => {
      if (documentFiltersEqual(prev, serverFilter)) return prev;
      return [...serverFilter];
    });
  }, [documentFilterStableKey, patchConv.isPending, conversationId, convs]);

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
  }, [messages, conversationId, streamingText]);

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
        const content = d.content ?? "";
        setTimeout(() => {
          setInput((prev) => {
            if (prev.trim() !== "") {
              return prev;
            }
            return content;
          });
        }, 0);
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
      setSendError(null);
      setStreaming(true);
      activeJobIdRef.current = accepted.jobId;
      try {
        await followLabJob(
          accepted,
          (s) => {
            const streamed = s.result?.streamedAnswer;
            if (typeof streamed === "string") {
              setStreamingText(streamed);
            }
          },
          { signal, throwOnFailed: false },
        );
        activeJobIdRef.current = null;
        setLastDone(null);
        resetStreaming();
        setStreaming(false);
        void refetchMessages();
      } catch (e) {
        activeJobIdRef.current = null;
        setStreaming(false);
        resetStreaming();
        if (e instanceof DOMException && e.name === "AbortError") {
          return;
        }
        setSendError(getSafeApiErrorMessage(e));
      } finally {
        setStreaming(false);
      }
    },
    [refetchMessages, resetStreaming, setLastDone, setStreaming, setStreamingText],
  );

  const send = useCallback(async () => {
    if (!input.trim() || isSending || isStreaming) return;
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const signal = abortRef.current.signal;
    setEditError(null);
    const text = input.trim();
    setInput("");
    const body: PostMessageBody = {
      content: text,
      llmModel: llmModelChoice.trim() ? llmModelChoice.trim() : null,
    };
    let targetConversationId = conversationId;
    setIsSending(true);
    setSendError(null);
    try {
      if (!targetConversationId) {
        const created = await createConv.mutateAsync();
        targetConversationId = created.id;
        selectConversation(created.id);
      }
      if (!targetConversationId) return;
      const accepted = await apiFetch<LabJobAcceptedDto>(
        apiProductPath(`/conversations/${targetConversationId}/messages`),
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
          signal,
        },
      );
      await runChatJob(accepted, signal);
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        setInput(text);
        return;
      }
      setInput(text);
      setSendError(getSafeApiErrorMessage(e));
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
    runChatJob,
    selectConversation,
  ]);

  const retryAssistant = useCallback(
    async (assistantMessageId: string) => {
      if (!conversationId || isSending) return;
      abortRef.current?.abort();
      abortRef.current = new AbortController();
      const signal = abortRef.current.signal;
      setIsSending(true);
      setSendError(null);
      try {
        const accepted = await apiFetch<LabJobAcceptedDto>(
          apiProductPath(`/conversations/${conversationId}/messages/${assistantMessageId}/retry`),
          { method: "POST", signal },
        );
        await runChatJob(accepted, signal);
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") {
          return;
        }
        setSendError(getSafeApiErrorMessage(e));
      } finally {
        setIsSending(false);
      }
    },
    [conversationId, isSending, runChatJob],
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
  }, [conversationId, editBody, editingUserMessageId, isSending, llmModelChoice, runChatJob, t]);

  const stop = useCallback(() => {
    abortRef.current?.abort();
    const jid = activeJobIdRef.current;
    if (jid) {
      void cancelChatJob(jid).catch(() => {});
      activeJobIdRef.current = null;
    }
    resetStreaming();
    setStreaming(false);
    void refetchMessages();
  }, [refetchMessages, resetStreaming, setStreaming]);

  const onPresetChange = (value: string) => {
    if (!conversationId || !value.trim()) return;
    setPresetSelectValue(value);
    patchConv.mutate({ conversationId, body: { presetId: value } });
  };

  const onLimitDocsChange = (checked: boolean) => {
    if (!conversationId) return;
    if (!checked) {
      setLimitDocs(false);
      setSelectedDocIds([]);
      pendingDocumentFilterRef.current = [];
      patchConv.mutate({ conversationId, body: { documentFilter: [] } });
      return;
    }
    void (async () => {
      const { data: freshDocs } = await refetchProjectDocuments();
      const ready =
        freshDocs?.filter((d) => d.status === "READY").map((d) => d.id) ?? [];
      if (ready.length === 0) {
        return;
      }
      setLimitDocs(true);
      setSelectedDocIds(ready);
      pendingDocumentFilterRef.current = ready;
      patchConv.mutate({ conversationId, body: { documentFilter: ready } });
    })();
  };

  const onDocToggle = (documentId: string, checked: boolean) => {
    if (!conversationId || !limitDocs) return;
    const next = checked
      ? Array.from(new Set([...selectedDocIds, documentId]))
      : selectedDocIds.filter((id) => id !== documentId);
    if (next.length === 0) {
      setLimitDocs(false);
      setSelectedDocIds([]);
      pendingDocumentFilterRef.current = [];
      patchConv.mutate({ conversationId, body: { documentFilter: [] } });
      return;
    }
    setSelectedDocIds(next);
    pendingDocumentFilterRef.current = next;
    patchConv.mutate({ conversationId, body: { documentFilter: next } });
  };

  if (!projectId) {
    return <p className="text-muted-foreground text-sm">{t("noActiveProject")}</p>;
  }

  return (
    <div className="flex h-[calc(100dvh-7rem)] min-h-[420px] flex-col gap-3 md:flex-row">
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
        <aside className="flex w-full shrink-0 flex-col gap-2 border-border border-b pb-3 md:w-52 md:border-b-0 md:border-r md:pb-0 md:pr-3">
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
            disabled={createConv.isPending}
            onClick={async () => {
              const c = await createConv.mutateAsync();
              selectConversation(c.id);
              void refetchMessages();
            }}
          >
            {t("newConversation")}
          </Button>
          <div className="flex max-h-48 flex-col gap-1 overflow-y-auto md:max-h-none md:flex-1">
            {convs?.map((c) => (
              <Button
                key={c.id}
                type="button"
                variant={conversationId === c.id ? "secondary" : "ghost"}
                size="sm"
                className="h-auto justify-start py-2 text-left"
                onClick={() => selectConversation(c.id)}
              >
                <span className="line-clamp-2 text-xs">{c.title}</span>
              </Button>
            ))}
          </div>
        </aside>
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-3">
        {conversationId && active ? (
          <header className="flex flex-wrap items-end gap-3 border-border border-b pb-3">
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
            <div className="pb-0.5">
              <MoveConversationDialog sourceProjectId={projectId} conversationId={conversationId} />
            </div>
          </header>
        ) : null}
        {conversationId && (
          <div className="flex flex-col gap-3 rounded-lg border bg-card/20 p-3 text-sm md:flex-row md:flex-wrap md:items-end md:gap-4">
            <div className="flex min-w-[12rem] flex-1 flex-col gap-1">
              <Label htmlFor="chat-llm-model">{t("modelLabel")}</Label>
              <select
                id="chat-llm-model"
                className={cn(
                  "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                  modelsError && "border-destructive",
                )}
                value={llmModelChoice}
                onChange={(e) => setLlmModelChoice(e.target.value)}
                aria-label={t("modelLabel")}
                disabled={!!modelsError}
              >
                <option value="">{t("modelDefault")}</option>
                {modelsCatalog?.allowlist
                  ?.filter((e) => e.type === "LLM")
                  .sort((a, b) => a.name.localeCompare(b.name))
                  .map((m) => {
                    const usable = m.inAllowlist && m.installedInOllama;
                    return (
                      <option key={m.name} value={m.name} disabled={!usable}>
                        {m.name}
                        {!m.installedInOllama ? ` (${t("modelNotInstalled")})` : ""}
                        {!m.inAllowlist ? ` (${t("modelNotAllowlisted")})` : ""}
                      </option>
                    );
                  })}
              </select>
              {modelsError && (
                <p className="text-destructive text-xs" role="alert">
                  {modelsErrorMessage}
                </p>
              )}
              {!modelsError && !modelsCatalog?.ollamaReachable && (
                <p className="text-muted-foreground text-xs">{t("ollamaUnreachable")}</p>
              )}
            </div>
            <div className="flex min-w-[12rem] flex-1 flex-col gap-1">
              <Label htmlFor="chat-preset">{t("presetLabel")}</Label>
              <select
                id="chat-preset"
                className={cn(
                  "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                  presetsError && "border-destructive",
                )}
                value={presetSelectValue}
                onChange={(e) => onPresetChange(e.target.value)}
                aria-label={t("presetLabel")}
                disabled={!!presetsError || patchConv.isPending}
              >
                {syntheticPresetOptionNeeded ? (
                  <option value={presetSelectValue}>
                    {resolvePresetSelectLabel(presets, presetSelectValue, {
                      systemSuffix: t("presetSystem"),
                      serverDefault: t("presetServerDefault"),
                    })}
                  </option>
                ) : null}
                {presets?.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.system ? ` (${t("presetSystem")})` : ""}
                  </option>
                ))}
              </select>
              {presetsError && <p className="text-destructive text-xs">{t("presetsLoadError")}</p>}
            </div>
            <div className="min-w-[min(100%,14rem)] flex-1 flex-col gap-2 md:max-w-md">
              <Label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  className="size-4 rounded border"
                  checked={limitDocs}
                  onChange={(e) => onLimitDocsChange(e.target.checked)}
                  disabled={!conversationId || patchConv.isPending}
                />
                {t("limitDocuments")}
              </Label>
              {limitDocs && (
                <ScrollArea className="h-32 rounded-md border p-2">
                  {docs?.length === 0 && (
                    <p className="text-muted-foreground text-xs">{t("noDocumentsInProject")}</p>
                  )}
                  {docs?.map((d) => (
                    <label
                      key={d.id}
                      className="flex cursor-pointer items-start gap-2 py-1 text-xs"
                    >
                      <input
                        type="checkbox"
                        className="mt-0.5 size-3.5 shrink-0 rounded border"
                        checked={selectedDocIds.includes(d.id)}
                        disabled={d.status !== "READY" || patchConv.isPending}
                        onChange={(e) => onDocToggle(d.id, e.target.checked)}
                      />
                      <span className="break-all">
                        {d.fileName}
                        {d.status !== "READY" ? (
                          <span className="text-muted-foreground"> ({d.status})</span>
                        ) : null}
                      </span>
                    </label>
                  ))}
                </ScrollArea>
              )}
            </div>
          </div>
        )}
        <div
          ref={scrollAreaRef}
          className="relative min-h-0 flex-1 space-y-3 overflow-y-auto rounded-lg border bg-card/30 p-3"
        >
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
                    router.push("/chat");
                  }}
                >
                  {t("backToConversations")}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={() => {
                    setConversationId(null);
                    router.push("/chat");
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
                  ? "ml-auto max-w-[85%] rounded-lg bg-primary px-3 py-2 text-primary-foreground text-sm"
                  : "mr-auto max-w-[85%] rounded-lg border bg-background px-3 py-2 text-sm"
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
                    className="resize-none border-primary-foreground/30 bg-background text-foreground"
                    disabled={isStreaming}
                  />
                  <div className="flex flex-wrap gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      className="h-8"
                      disabled={isStreaming || isSending || !editBody.trim()}
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
                  <p className="whitespace-pre-wrap">{m.content}</p>
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
          {isStreaming && streamingText && (
            <div className="mr-auto max-w-[85%] rounded-lg border border-dashed bg-muted/20 px-3 py-2 text-sm">
              <p className="whitespace-pre-wrap">{streamingText}</p>
            </div>
          )}
          <div ref={bottomRef} />
        </div>
        <div className="flex flex-col gap-2">
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
          {patchConv.isError && (
            <p className="text-destructive text-xs" role="alert">
              {t("patchError")}
            </p>
          )}
          {isSending && !isStreaming ? (
            <p className="text-muted-foreground text-xs" aria-live="polite">
              {t("sending")}
            </p>
          ) : null}
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={t("placeholder")}
            rows={3}
            className="resize-none"
            aria-busy={isSending || isStreaming}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey && !isSending && !isStreaming) {
                e.preventDefault();
                void send();
              }
            }}
            disabled={isSending || isStreaming}
          />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" size="sm" disabled={!isStreaming} onClick={() => stop()}>
              {t("stop")}
            </Button>
            <Button
              type="button"
              size="sm"
              disabled={!input.trim() || isSending || isStreaming}
              onClick={() => void send()}
            >
              {t("send")}
            </Button>
          </div>
        </div>
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
