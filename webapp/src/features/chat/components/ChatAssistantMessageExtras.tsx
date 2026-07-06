"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import type { MessageDto } from "@/types/api";
import { formatClassifierFallbackNote } from "@/lib/product-copy";
import { dedupeChatSourcesForDisplay } from "@/features/sources/lib/dedupe-chat-sources";

function coerceBool(v: unknown): boolean {
  return v === true || v === "true" || v === 1 || v === "1";
}

export function isAssistantMessageComplete(status: string | null | undefined): boolean {
  const normalized = status?.trim();
  if (!normalized) return true;
  return normalized === "DONE";
}

function metadataString(meta: Record<string, unknown> | null | undefined, key: string): string | null {
  const value = meta?.[key];
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function messageMetadataString(message: MessageDto, key: string): string | null {
  return metadataString(message.executionMetadata, key);
}

function messageMetadataList(message: MessageDto, key: string): string[] {
  const value = message.executionMetadata?.[key];
  if (Array.isArray(value)) return value.map(String).filter(Boolean);
  if (typeof value === "string" && value.trim()) return value.split("|").map((x) => x.trim()).filter(Boolean);
  return [];
}

function messageMetadataBool(message: MessageDto, key: string): boolean {
  return coerceBool(message.executionMetadata?.[key]);
}

function messageMetadataNumber(message: MessageDto, key: string): number | null {
  const value = message.executionMetadata?.[key];
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function hasRuntimeTraceMetadata(message: MessageDto): boolean {
  const meta = message.executionMetadata ?? {};
  return [
    "traceId",
    "workflowName",
    "selectedSnapshotIds",
    "retrievalEffectiveTopK",
    "retrievalEffectiveSimilarityThreshold",
    "retrievalDenseFetchLimit",
    "retrievalDenseCandidateCount",
    "retrievalAfterFilterCount",
    "retrievalAfterCompressionCount",
    "retrievalContextReductionReason",
    "requestedDate",
    "dateMismatchDetected",
    "groundingPolicyApplied",
    "exactDocumentMatch",
    "topSourceDate",
    "closestAvailableDate",
    "candidateSourceCountBeforeDateFilter",
    "candidateSourceCountAfterDateFilter",
    "dateBoostApplied",
    "documentBound",
    "answerPolicy",
    "groundingPolicy",
    "classifierModelIdUsed",
    "classifierModelId",
    "classifierLabel",
    "classifierStatus",
    "predictedQueryType",
  ].some((key) => meta[key] !== undefined && meta[key] !== null && String(meta[key]).trim() !== "");
}

function sourceChunkIndex(source: Record<string, unknown>): string | null {
  const direct = source.chunkIndex ?? source.chunk_index;
  if (typeof direct === "number") return String(direct);
  if (typeof direct === "string" && direct.trim()) return direct.trim();
  const meta = source.metadata;
  if (meta && typeof meta === "object") {
    const fromMeta = meta as Record<string, unknown>;
    const chunkId = metadataString(fromMeta, "chunkId");
    if (chunkId) return chunkId;
    const idx = fromMeta.chunkIndex ?? fromMeta.chunk_index;
    if (typeof idx === "number") return String(idx);
    if (typeof idx === "string" && idx.trim()) return idx.trim();
  }
  return null;
}

function sourceDetectedDate(source: Record<string, unknown>): string | null {
  const direct = source.detectedDate ?? source.documentDate ?? source.date_iso ?? source.date;
  if (typeof direct === "string" && direct.trim()) return direct.trim();
  const meta = source.metadata;
  if (meta && typeof meta === "object") {
    const fromMeta = meta as Record<string, unknown>;
    return metadataString(fromMeta, "detectedDate") ?? metadataString(fromMeta, "documentDate") ?? metadataString(fromMeta, "date_iso");
  }
  return null;
}

function sourceSnippet(source: Record<string, unknown>): string | null {
  const value = source.snippet ?? source.excerpt ?? source.text ?? source.content;
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function sourceScore(source: Record<string, unknown>): string | null {
  const value = source.distance ?? source.score;
  if (typeof value !== "number") {
    return value != null ? String(value) : null;
  }
  // Lower is better (embedding distance), not a 0-1 "similarity" percentage. Label explicitly
  // so it isn't misread as, or compared against, the "similarity threshold" setting (higher-is-better).
  const label = typeof source.distanceLabel === "string" && source.distanceLabel.trim() ? source.distanceLabel : "distance";
  return `${label}=${value.toFixed(4)}`;
}

function sourceDateMismatch(sourceDate: string | null, requestedDate: string | null): boolean {
  return Boolean(sourceDate && requestedDate && sourceDate !== requestedDate);
}

function sourceSupportsAnswer(source: Record<string, unknown>): boolean {
  const meta = source.metadata;
  if (meta && typeof meta === "object") {
    const m = meta as Record<string, unknown>;
    if (m.alternativeOnly === true) return false;
    if (m.supportingAnswer === false) return false;
  }
  return true;
}

function sourceIsAlternativeOnly(source: Record<string, unknown>): boolean {
  const meta = source.metadata;
  if (meta && typeof meta === "object") {
    const m = meta as Record<string, unknown>;
    return m.alternativeOnly === true || m.supportingAnswer === false;
  }
  return false;
}

function SourceChunkDetails({
  source,
  requestedDate,
  t,
}: Readonly<{
  source: Record<string, unknown>;
  requestedDate: string | null;
  t: ReturnType<typeof useTranslations<"Chat">>;
}>) {
  const detectedDate = sourceDetectedDate(source);
  const excerpt = sourceSnippet(source);
  const chunk = sourceChunkIndex(source);
  const mismatched = sourceDateMismatch(detectedDate, requestedDate);
  const alternative = sourceIsAlternativeOnly(source);

  return (
    <>
      {detectedDate ? <p className="font-mono text-muted-foreground mt-0.5">date={detectedDate}</p> : null}
      {chunk ? <p className="font-mono text-muted-foreground mt-0.5">chunk={chunk}</p> : null}
      {alternative ? (
        <p className="mt-0.5 text-amber-700 dark:text-amber-300" data-testid="chat-source-alternative">
          {t("sourceAlternativeOnly")}
        </p>
      ) : null}
      {mismatched && !alternative ? (
        <p className="mt-0.5 text-amber-700 dark:text-amber-300" data-testid="chat-date-warning">
          {t("chatSourceDateMismatch", { date: requestedDate ?? "" })}
        </p>
      ) : null}
      {!sourceSupportsAnswer(source) && !alternative && !mismatched ? (
        <p className="mt-0.5 text-muted-foreground">{t("sourceNotSupportingAnswer")}</p>
      ) : null}
      {excerpt ? (
        <p className="text-muted-foreground mt-0.5 line-clamp-3 whitespace-pre-wrap break-words">{String(excerpt)}</p>
      ) : null}
    </>
  );
}

function DedupedSourceGroup({
  group,
  requestedDate,
  t,
}: Readonly<{
  group: ReturnType<typeof dedupeChatSourcesForDisplay>[number];
  requestedDate: string | null;
  t: ReturnType<typeof useTranslations<"Chat">>;
}>) {
  const [expanded, setExpanded] = useState(false);
  const principal = group.chunks[0];
  const principalScore = sourceScore(principal);
  const chunkCount = group.chunks.length;

  return (
    <li className="space-y-1" data-testid="chat-source-group">
      <div className="min-w-0 rounded-sm bg-muted/20 px-2 py-1">
        <div className="flex min-w-0 items-baseline justify-between gap-2">
          <span className="truncate font-medium break-all">{group.displayName}</span>
          {principalScore ? <span className="shrink-0 font-mono text-muted-foreground">{principalScore}</span> : null}
        </div>
        {chunkCount === 1 ? (
          <SourceChunkDetails source={principal} requestedDate={requestedDate} t={t} />
        ) : (
          <>
            <button
              type="button"
              className="text-primary mt-1 text-[10px] underline underline-offset-2"
              data-testid="chat-source-chunks-toggle"
              onClick={() => setExpanded((v) => !v)}
            >
              {expanded ? t("sourceChunksHide") : t("sourceChunksShow", { count: chunkCount })}
            </button>
            {!expanded ? <SourceChunkDetails source={principal} requestedDate={requestedDate} t={t} /> : null}
          </>
        )}
      </div>
      {expanded && chunkCount > 1 ? (
        <ul className="space-y-1 pl-2" data-testid="chat-source-chunks">
          {group.chunks.map((chunk, idx) => (
            <li key={`${group.key}-${idx}`} className="rounded-sm bg-muted/10 px-2 py-1">
              <div className="flex items-baseline justify-between gap-2">
                {sourceChunkIndex(chunk) ? (
                  <span className="font-mono text-muted-foreground text-[10px]">chunk={sourceChunkIndex(chunk)}</span>
                ) : (
                  <span className="text-muted-foreground text-[10px]">#{idx + 1}</span>
                )}
                {sourceScore(chunk) ? (
                  <span className="shrink-0 font-mono text-muted-foreground text-[10px]">{sourceScore(chunk)}</span>
                ) : null}
              </div>
              <SourceChunkDetails source={chunk} requestedDate={requestedDate} t={t} />
            </li>
          ))}
        </ul>
      ) : null}
    </li>
  );
}

export function ChatAssistantMessageExtras({ message }: Readonly<{ message: MessageDto }>) {
  const t = useTranslations("Chat");

  if (message.role !== "ASSISTANT" || !isAssistantMessageComplete(message.status)) {
    return null;
  }

  const rawSources = Array.isArray(message.sources) ? message.sources : [];
  const sourceGroups = dedupeChatSourcesForDisplay(rawSources);
  const showTrace =
    hasRuntimeTraceMetadata(message) || Boolean(messageMetadataString(message, "abstentionReason"));
  const requestedDate = messageMetadataString(message, "requestedDate");
  const effectiveTopK = messageMetadataNumber(message, "retrievalEffectiveTopK");
  const threshold = messageMetadataNumber(message, "retrievalEffectiveSimilarityThreshold");
  const denseCandidates = messageMetadataNumber(message, "retrievalDenseCandidateCount");
  const afterFilter = messageMetadataNumber(message, "retrievalAfterFilterCount");
  const afterCompression = messageMetadataNumber(message, "retrievalAfterCompressionCount");
  const reductionReason = messageMetadataString(message, "retrievalContextReductionReason");
  const showReductionReason =
    effectiveTopK != null &&
    afterCompression != null &&
    afterCompression < effectiveTopK &&
    Boolean(reductionReason);
  const reductionReasonLabel =
    reductionReason === "fewer_dense_hits"
      ? t("chatTraceReductionReason.fewer_dense_hits")
      : reductionReason === "threshold_or_scope"
        ? t("chatTraceReductionReason.threshold_or_scope")
        : reductionReason === "section_merge"
          ? t("chatTraceReductionReason.section_merge")
          : reductionReason === "compression_drop"
            ? t("chatTraceReductionReason.compression_drop")
            : t("chatTraceReductionReason.context_budget_or_dedup");

  return (
    <details className="group mt-2" data-testid="chat-message-metadata">
      <summary
        data-testid="chat-message-metadata-toggle"
        className="cursor-pointer list-none text-muted-foreground text-xs font-medium hover:text-foreground [&::-webkit-details-marker]:hidden"
      >
        {t("chatMoreInformationLabel")}
      </summary>
      <div className="mt-2 space-y-2 border-border border-t pt-2" data-testid="chat-message-metadata-panel">
        {sourceGroups.length > 0 ? (
          <div data-testid="chat-sources">
            <p className="text-muted-foreground text-[11px] font-medium">
              {t("chatSourcesHeading", { count: sourceGroups.length })}
            </p>
            <ul className="mt-1 space-y-1 text-[11px]">
              {sourceGroups.slice(0, 5).map((group) => (
                <DedupedSourceGroup key={group.key} group={group} requestedDate={requestedDate} t={t} />
              ))}
            </ul>
          </div>
        ) : (
          <p className="text-[11px] text-muted-foreground" data-testid="chat-sources">
            {t("sourcesEmptyShort")}
          </p>
        )}
        {messageMetadataBool(message, "dateMismatchDetected") ? (
          <div
            className="rounded-md border border-amber-500/40 bg-amber-500/10 px-2 py-1 text-[11px]"
            data-testid="chat-date-warning"
          >
            {t("chatDateGroundingWarning", {
              date: messageMetadataString(message, "requestedDate") ?? "date",
            })}
          </div>
        ) : null}
        {showTrace ? (
          <details className="rounded-md border bg-muted/20 px-2 py-1 text-[11px]" data-testid="chat-trace-disclosure">
            <summary className="cursor-pointer font-medium text-muted-foreground">
              {t("chatTraceTechnicalSummary")}
            </summary>
            <div className="mt-2" data-testid="chat-trace">
              <p className="font-medium text-muted-foreground">{t("chatTraceHeading")}</p>
              <dl className="mt-1 grid grid-cols-1 gap-1 font-mono">
              {messageMetadataString(message, "traceId") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldTraceId")}</dt>
                  <dd className="break-all">{messageMetadataString(message, "traceId")}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "workflowName") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldWorkflow")}</dt>
                  <dd>{messageMetadataString(message, "workflowName")}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "classifierModelIdUsed") ||
              messageMetadataString(message, "classifierModelId") ? (
                <div className="flex justify-between gap-2" data-testid="chat-trace-classifier-model">
                  <dt className="text-muted-foreground">{t("chatTraceFieldClassifierModel")}</dt>
                  <dd className="break-all">
                    {messageMetadataString(message, "classifierModelIdUsed") ??
                      messageMetadataString(message, "classifierModelId")}
                  </dd>
                </div>
              ) : null}
              {messageMetadataString(message, "classifierLabel") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldClassifierLabel")}</dt>
                  <dd>{messageMetadataString(message, "classifierLabel")}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "classifierStatus") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldClassifierStatus")}</dt>
                  <dd>{messageMetadataString(message, "classifierStatus")}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "predictedQueryType") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldPredictedQueryType")}</dt>
                  <dd>{messageMetadataString(message, "predictedQueryType")}</dd>
                </div>
              ) : null}
              {message.executionMetadata?.classifierFallback === true ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldClassifierFallback")}</dt>
                  <dd>true</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "classifierFallbackReason") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldClassifierFallbackReason")}</dt>
                  <dd>
                    {formatClassifierFallbackNote(messageMetadataString(message, "classifierFallbackReason"), t)}
                  </dd>
                </div>
              ) : null}
              {messageMetadataString(message, "requestedDate") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldRequestedDate")}</dt>
                  <dd>{messageMetadataString(message, "requestedDate")}</dd>
                </div>
              ) : null}
              {messageMetadataList(message, "selectedSnapshotIds").length > 0 ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldSnapshot")}</dt>
                  <dd className="break-all">{messageMetadataList(message, "selectedSnapshotIds").join(", ")}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "topSourceDate") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldTopSourceDate")}</dt>
                  <dd>{messageMetadataString(message, "topSourceDate")}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "closestAvailableDate") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldClosestAvailableDate")}</dt>
                  <dd>{messageMetadataString(message, "closestAvailableDate")}</dd>
                </div>
              ) : null}
              {message.executionMetadata?.exactDocumentMatch != null ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldExactDocumentMatch")}</dt>
                  <dd>{String(message.executionMetadata.exactDocumentMatch)}</dd>
                </div>
              ) : null}
              {message.executionMetadata?.documentBound != null ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldDocumentBound")}</dt>
                  <dd>{String(message.executionMetadata.documentBound)}</dd>
                </div>
              ) : null}
              {message.executionMetadata?.candidateSourceCountBeforeDateFilter != null ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldDateFilter")}</dt>
                  <dd>
                    {String(message.executionMetadata.candidateSourceCountBeforeDateFilter)}
                    {" -> "}
                    {String(message.executionMetadata.candidateSourceCountAfterDateFilter ?? "?")}
                  </dd>
                </div>
              ) : null}
              {afterCompression != null ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldRetrieval")}</dt>
                  <dd>
                    {t("chatTraceRetrievalSummary", {
                      effectiveTopK: effectiveTopK != null ? String(effectiveTopK) : "?",
                      threshold: threshold != null ? String(threshold) : "?",
                      dense: denseCandidates != null ? String(denseCandidates) : "?",
                      filtered: afterFilter != null ? String(afterFilter) : "?",
                      final: String(afterCompression),
                    })}
                  </dd>
                </div>
              ) : null}
              {showReductionReason ? (
                <div className="col-span-full" data-testid="chat-trace-reduction-reason">
                  <dt className="text-muted-foreground">{t("chatTraceFieldContextReductionReason")}</dt>
                  <dd>{reductionReasonLabel}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "groundingPolicyApplied") ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t("chatTraceFieldDateGrounding")}</dt>
                  <dd>{messageMetadataString(message, "groundingPolicyApplied")}</dd>
                </div>
              ) : null}
              {messageMetadataString(message, "abstentionReason") ? (
                <div className="col-span-full">
                  <dt className="text-muted-foreground">{t("chatTraceFieldAbstentionReason")}</dt>
                  <dd>{messageMetadataString(message, "abstentionReason")}</dd>
                </div>
              ) : null}
            </dl>
            </div>
          </details>
        ) : null}
      </div>
    </details>
  );
}
