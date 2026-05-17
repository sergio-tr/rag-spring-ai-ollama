"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { HelpPopover } from "@/features/help/HelpPopover";
import { LabExperimentalDatasetPanel } from "@/features/lab/components/lab-experimental-dataset-panel";
import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import type { ExperimentalDatasetListItemDto, LabValidationIssueDto } from "@/types/api";
import { Link } from "@/navigation";
import { useTranslations } from "next-intl";
import { useEffect } from "react";
import { ApiError } from "@/lib/api-client";

export default function LabOverviewPage() {
  const t = useTranslations("Lab");
  const tHelp = useTranslations("Help");
  const { data: status, isError, isLoading, refetch, error: statusError } = useLabStatus();
  const experimentalList = useExperimentalDatasetsQuery();

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (window.location.hash !== "#datasets") return;

    let cancelled = false;
    let attempts = 0;
    const maxAttempts = 25;

    const tick = () => {
      if (cancelled) return;
      const el = document.getElementById("datasets");
      if (el) {
        try {
          el.scrollIntoView({ behavior: "instant" as ScrollBehavior, block: "start" });
        } catch {
          el.scrollIntoView();
        }
        return;
      }
      attempts += 1;
      if (attempts < maxAttempts) {
        window.setTimeout(tick, 60);
      }
    };
    tick();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="space-y-6">
      <Card data-testid="lab-control-panel">
        <CardHeader>
          <CardTitle>{t("controlPanelTitle")}</CardTitle>
          <CardDescription>{t("controlPanelDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-2">
            <p className="text-sm font-medium">{t("step1Title")}</p>
            <p className="text-muted-foreground text-xs">{t("step1Body")}</p>
            <div className="flex flex-wrap gap-2 text-xs">
              <Badge variant={status?.referenceBundleValid === true ? "default" : "secondary"}>
                {status?.referenceBundleValid === true
                  ? t("step1ReferenceBundleValid")
                  : t("step1ReferenceBundleUnknown")}
              </Badge>
              <Badge variant={(experimentalList.data?.length ?? 0) > 0 ? "outline" : "secondary"}>
                {t("step1UploadsBadge", { n: experimentalList.data?.length ?? 0 })}
              </Badge>
              <Link
                className="text-primary inline-flex items-center underline underline-offset-4"
                href="#datasets"
              >
                {t("step1JumpToDatasets")}
              </Link>
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium">{t("step2Title")}</p>
            <p className="text-muted-foreground text-xs">{t("step2Body")}</p>
            <div className="grid gap-3 sm:grid-cols-3">
              <WorkflowCard
                title={t("flowLlmTitle")}
                body={t("flowLlmBody")}
                datasetLine={t("flowLlmDataset")}
                outputLine={t("flowLlmOutput")}
                href="/lab/evaluation/llm"
                cta={t("flowCtaOpen")}
                t={t}
              />
              <WorkflowCard
                title={t("flowEmbeddingTitle")}
                body={t("flowEmbeddingBody")}
                datasetLine={t("flowEmbeddingDataset")}
                outputLine={t("flowEmbeddingOutput")}
                href="/lab/evaluation/embedding"
                cta={t("flowCtaOpen")}
                t={t}
              />
              <WorkflowCard
                title={t("flowRagTitle")}
                body={t("flowRagBody")}
                datasetLine={t("flowRagDataset")}
                outputLine={t("flowRagOutput")}
                href="/lab/evaluation/rag"
                cta={t("flowCtaOpen")}
                t={t}
              />
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium">{t("step3Title")}</p>
            <p className="text-muted-foreground text-xs">{t("step3Body")}</p>
            <div className="rounded-md border bg-muted/20 p-3 text-xs">
              <p className="text-muted-foreground">{t("step3Hint")}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
          <div className="min-w-0 flex-1 space-y-1.5">
            <CardTitle>{t("overviewTitle")}</CardTitle>
            <CardDescription>{t("overviewDescription")}</CardDescription>
          </div>
          <HelpPopover
            triggerAriaLabel={tHelp("labOverviewTriggerLabel")}
            title={tHelp("labOverviewTitle")}
            message={tHelp("labOverviewMessage")}
            details={tHelp("labOverviewDetails")}
          />
        </CardHeader>
        <CardContent className="space-y-4">
          {isError && (
            <p className="text-destructive text-sm" role="alert">
              {statusError instanceof ApiError && statusError.status === 401
                ? "Authentication required."
                : statusError instanceof ApiError && statusError.status === 403
                  ? "Insufficient permissions."
                  : statusError instanceof ApiError && statusError.status === 404
                    ? "Lab resources not found."
                    : t("statusError")}
            </p>
          )}
          {isLoading && !status && <p className="text-muted-foreground text-sm">{t("statusLoading")}</p>}
          {status && (
            <>
              {status.message?.trim() ? (
                <details className="text-sm">
                  <summary className="cursor-pointer text-muted-foreground">{t("statusServerNoteToggle")}</summary>
                  <p className="text-muted-foreground mt-2 text-xs">{status.message}</p>
                </details>
              ) : null}
              <div className="grid gap-3 sm:grid-cols-2">
                <Card className="border-dashed">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base">{t("statusDatasets")}</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge
                        variant={
                          (status.datasetKindsReady ?? status.datasets.enabled) ? "default" : "secondary"
                        }
                      >
                        {(status.datasetKindsReady ?? status.datasets.enabled) ? t("statusOn") : t("statusOff")}
                      </Badge>
                      {status.countsByDatasetKind ? (
                        <span className="text-muted-foreground text-xs">
                          {t("statusTypedDatasetKindsLine", {
                            llm: status.countsByDatasetKind.llmReaderQuestions ?? 0,
                            emb: status.countsByDatasetKind.embeddingRetrievalQueries ?? 0,
                            rag: status.countsByDatasetKind.ragPresetQuestions ?? 0,
                          })}
                        </span>
                      ) : (
                        <span className="text-muted-foreground text-xs">{t("statusCountsUnavailable")}</span>
                      )}
                    </div>
                    {!(status.datasetKindsReady ?? status.datasets.enabled) ? (
                      <p className="text-muted-foreground text-xs">{t("statusDatasetsHint")}</p>
                    ) : null}
                  </CardContent>
                </Card>
                <Card className="border-dashed">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base">{t("statusEvaluations")}</CardTitle>
                  </CardHeader>
                  <CardContent className="flex flex-wrap gap-2 text-xs">
                    <Badge variant="outline">LLM: {status.evaluations.llm ? t("statusOn") : t("statusOff")}</Badge>
                    <Badge variant="outline">RAG: {status.evaluations.rag ? t("statusOn") : t("statusOff")}</Badge>
                    <Badge variant="outline">
                      {t("statusClassifierProxy")}:{" "}
                      {status.evaluations.classifierProxy ? t("statusOn") : t("statusOff")}
                    </Badge>
                    {status.evaluations.asyncJobs !== false ? (
                      <Badge variant="outline">{t("statusAsyncJobs")}</Badge>
                    ) : null}
                  </CardContent>
                </Card>
                <Card className="border-dashed sm:col-span-2">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base">{t("statusClassifier")}</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    <div className="flex flex-wrap gap-2">
                      <Badge variant={status.classifier.configured ? "default" : "destructive"}>
                        {status.classifier.configured ? t("statusClassifierOk") : t("statusClassifierDown")}
                      </Badge>
                    </div>
                    {!status.classifier.configured ? (
                      <p className="text-muted-foreground text-xs">{t("statusClassifierHint")}</p>
                    ) : null}
                  </CardContent>
                </Card>
                <Card className="border-dashed sm:col-span-2">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base">{t("referenceBundleCardTitle")}</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3 text-sm">
                    <div className="flex flex-wrap gap-2">
                      <Badge variant={status.referenceBundleAvailable === true ? "default" : "secondary"}>
                        {status.referenceBundleAvailable === true
                          ? t("referenceBundlePresent")
                          : t("referenceBundleMissing")}
                      </Badge>
                      <Badge variant={status.referenceBundleValid === true ? "default" : "destructive"}>
                        {status.referenceBundleValid === true
                          ? t("referenceBundleValidYes")
                          : t("referenceBundleValidNo")}
                      </Badge>
                      {status.protocolVersion?.trim() ? (
                        <Badge variant="outline">
                          {t("referenceBundleProtocol", { version: status.protocolVersion.trim() })}
                        </Badge>
                      ) : null}
                    </div>
                    {status.referenceBundleAvailable !== true ? (
                      <p className="text-muted-foreground text-xs">{t("referenceBundleMissingHint")}</p>
                    ) : null}
                    {status.referenceBundleAvailable === true && status.referenceBundleValid !== true ? (
                      <p className="text-muted-foreground text-xs">{t("referenceBundleInvalidHint")}</p>
                    ) : null}
                    {status.validationIssues && status.validationIssues.length > 0 ? (
                      <div className="space-y-2">
                        <p className="text-muted-foreground text-xs font-medium">{t("referenceBundleIssuesTitle")}</p>
                        <ul className="max-h-40 list-disc space-y-1 overflow-auto pl-4 text-xs">
                          {status.validationIssues.slice(0, 12).map((issue: LabValidationIssueDto, idx: number) => (
                            <li key={`${issue.code}-${issue.rowNumber}-${issue.column}-${idx}`}>
                              <span className="font-mono">{issue.code}</span>
                              {issue.sheet ? (
                                <span className="text-muted-foreground">
                                  {" "}
                                  ({issue.sheet}
                                  {issue.rowNumber > 0 ? ` · ${t("experimentalDatasetIssueRow", { n: issue.rowNumber })}` : ""}
                                  {issue.column ? ` · ${issue.column}` : ""})
                                </span>
                              ) : null}
                              {": "}
                              {issue.message}
                            </li>
                          ))}
                        </ul>
                        {status.validationIssues.length > 12 ? (
                          <p className="text-muted-foreground text-xs">
                            {t("referenceBundleIssuesTruncated", { n: status.validationIssues.length - 12 })}
                          </p>
                        ) : null}
                      </div>
                    ) : status.referenceBundleAvailable === true && status.referenceBundleValid === true ? (
                      <p className="text-muted-foreground text-xs">{t("referenceBundleIssuesNone")}</p>
                    ) : null}
                  </CardContent>
                </Card>
                <Card className="border-dashed sm:col-span-2">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base">{t("overviewUploadedDatasetsTitle")}</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    {experimentalList.isLoading ? (
                      <p className="text-muted-foreground text-xs">{t("overviewUploadedDatasetsLoading")}</p>
                    ) : experimentalList.isError ? (
                      <p className="text-destructive text-xs" role="alert">
                        {t("overviewUploadedDatasetsError")}
                      </p>
                    ) : (
                      <>
                        <p className="text-muted-foreground text-xs">
                          {t("overviewUploadedDatasetsCounts", {
                            total: experimentalList.data?.length ?? 0,
                            custom: (experimentalList.data ?? []).filter((d) => !d.readOnly).length,
                          })}
                        </p>
                        <UploadedValidationBadges rows={experimentalList.data ?? []} t={t} />
                        <DatasetOverviewTable rows={experimentalList.data ?? []} t={t} />
                      </>
                    )}
                  </CardContent>
                </Card>
              </div>
              <details className="text-xs">
                <summary className="cursor-pointer text-muted-foreground">{t("statusRawToggle")}</summary>
                <pre className="bg-muted/40 mt-2 max-h-[240px] overflow-auto rounded-md border p-3">
                  {JSON.stringify(status, null, 2)}
                </pre>
              </details>
            </>
          )}
          <ButtonLikeRefetch onClick={() => void refetch()} label={t("statusRefresh")} />
        </CardContent>
      </Card>

      <div id="datasets" />
      
      <LabExperimentalDatasetPanel />

    </div>
  );
}

function WorkflowCard({
  title,
  body,
  datasetLine,
  outputLine,
  href,
  cta,
  t,
}: Readonly<{
  title: string;
  body: string;
  datasetLine: string;
  outputLine: string;
  href: string;
  cta: string;
  t: (key: string) => string;
}>) {
  return (
    <div className="rounded-md border bg-muted/20 p-3 text-sm">
      <p className="font-medium">{title}</p>
      <p className="text-muted-foreground mt-1 text-xs leading-relaxed">{body}</p>
      <p className="text-muted-foreground mt-2 text-xs">
        <span className="font-medium">{t("flowMetaDataset")}:</span> {datasetLine}
      </p>
      <p className="text-muted-foreground mt-1 text-xs">
        <span className="font-medium">{t("flowMetaOutput")}:</span> {outputLine}
      </p>
      <Link className="text-primary mt-2 inline-block text-xs underline-offset-4 hover:underline" href={href}>
        {cta}
      </Link>
    </div>
  );
}

function UploadedValidationBadges({
  rows,
  t,
}: Readonly<{
  rows: ExperimentalDatasetListItemDto[];
  t: (key: string, values?: Record<string, string | number>) => string;
}>) {
  const valid = rows.filter((r) => r.validationStatus === "VALID").length;
  const invalid = rows.filter((r) => r.validationStatus === "INVALID").length;
  const other = rows.length - valid - invalid;
  if (rows.length === 0) {
    return <p className="text-muted-foreground text-xs">{t("overviewUploadedDatasetsEmpty")}</p>;
  }
  return (
    <div className="flex flex-wrap gap-2 text-xs">
      {valid > 0 ? (
        <Badge variant="default">{t("overviewUploadedValidBadge", { count: valid })}</Badge>
      ) : null}
      {invalid > 0 ? (
        <Badge variant="destructive">{t("overviewUploadedInvalidBadge", { count: invalid })}</Badge>
      ) : null}
      {other > 0 ? <Badge variant="secondary">{t("overviewUploadedOtherBadge", { count: other })}</Badge> : null}
    </div>
  );
}

function DatasetOverviewTable({
  rows,
  t,
}: Readonly<{
  rows: ExperimentalDatasetListItemDto[];
  t: (key: string, values?: Record<string, string | number>) => string;
}>) {
  if (rows.length === 0) return null;
  return (
    <div className="max-h-72 overflow-auto rounded-md border" data-testid="lab-dataset-overview-table">
      <table className="w-full text-left text-xs">
        <thead className="bg-muted/50 sticky top-0">
          <tr>
            <th className="p-2 font-medium">{t("datasetTableColName")}</th>
            <th className="p-2 font-medium">{t("datasetTableColType")}</th>
            <th className="p-2 font-medium">{t("datasetTableColLlm")}</th>
            <th className="p-2 font-medium">{t("datasetTableColEmb")}</th>
            <th className="p-2 font-medium">{t("datasetTableColRag")}</th>
            <th className="p-2 font-medium">{t("datasetTableColPresets")}</th>
            <th className="p-2 font-medium">{t("datasetTableColStatus")}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-border border-t">
              <td className="p-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="truncate">{r.name ?? t("experimentalDatasetUnnamed")}</span>
                  {r.isReferenceBundle ? <Badge variant="outline">{t("datasetOriginReference")}</Badge> : null}
                  {r.isDemoDataset ? <Badge variant="destructive">{t("datasetTableBadgeDemo")}</Badge> : null}
                </div>
              </td>
              <td className="p-2 font-mono">{r.experimentalDatasetType}</td>
              <td className="p-2 font-mono">{r.questionCounts.llmReaderQuestions ?? 0}</td>
              <td className="p-2 font-mono">{r.questionCounts.embeddingQueries ?? 0}</td>
              <td className="p-2 font-mono">{r.questionCounts.ragPresetQuestions ?? 0}</td>
              <td className="p-2 font-mono">{r.questionCounts.presetCatalog ?? 0}</td>
              <td className="p-2">
                <Badge
                  variant={
                    r.validationStatus === "VALID" ? "default" : r.validationStatus === "INVALID" ? "destructive" : "secondary"
                  }
                >
                  {r.validationStatus}
                </Badge>
                {r.isDemoDataset ? (
                  <p className="text-destructive mt-1 text-[11px]">{t("datasetBlockedDemo")}</p>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ButtonLikeRefetch({
  onClick,
  label,
}: Readonly<{ onClick: () => void; label: string }>) {
  return (
    <button
      type="button"
      className="text-primary text-sm underline-offset-4 hover:underline"
      onClick={onClick}
    >
      {label}
    </button>
  );
}
