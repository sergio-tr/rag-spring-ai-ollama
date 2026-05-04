"use client";

import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { HelpPopover } from "@/features/help/HelpPopover";
import { useExperimentalDatasetsQuery, useUploadExperimentalDatasetMutation } from "@/features/lab/hooks/use-experimental-datasets";
import {
  EXPERIMENTAL_DATASET_TEMPLATE_KINDS,
  downloadExperimentalDatasetTemplate,
  suggestedTemplateFilename,
  triggerBrowserBlobDownload,
} from "@/features/lab/lib/experimental-datasets-api";
import { getSafeApiErrorMessage } from "@/lib/api-client";
import type {
  ExperimentalDatasetTemplateKind,
  ExperimentalDatasetValidationReportDto,
} from "@/types/api";
import { useTranslations } from "next-intl";

function ValidationReportBlock({
  report,
  title,
  t,
}: Readonly<{
  report: ExperimentalDatasetValidationReportDto;
  title: string;
  t: (key: string, values?: Record<string, string | number>) => string;
}>) {
  return (
    <div className="space-y-2 rounded-md border bg-muted/30 p-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{title}</span>
        {report.hasErrors ? (
          <Badge variant="destructive">{t("experimentalDatasetBadgeHasErrors")}</Badge>
        ) : (
          <Badge variant="secondary">{t("experimentalDatasetBadgeNoErrors")}</Badge>
        )}
        {report.hasWarnings ? (
          <Badge variant="outline">{t("experimentalDatasetBadgeWarnings")}</Badge>
        ) : null}
      </div>
      {report.issues.length === 0 ? (
        <p className="text-muted-foreground text-xs">{t("experimentalDatasetReportNoIssues")}</p>
      ) : (
        <ul className="max-h-48 list-disc space-y-1 overflow-auto pl-4 text-xs">
          {report.issues.map((issue, idx) => (
            <li key={`${issue.code}-${issue.rowNumber}-${issue.column}-${idx}`}>
              <span className="font-mono">{issue.code}</span>
              {issue.sheet ? (
                <>
                  {" "}
                  <span className="text-muted-foreground">
                    ({issue.sheet}
                    {issue.rowNumber > 0 ? ` ${t("experimentalDatasetIssueRow", { n: issue.rowNumber })}` : ""}
                    {issue.column ? ` · ${issue.column}` : ""})
                  </span>
                </>
              ) : null}
              {": "}
              {issue.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function LabExperimentalDatasetPanel() {
  const t = useTranslations("Lab");
  const tHelp = useTranslations("Help");
  const { data: datasets, isLoading, isError, refetch } = useExperimentalDatasetsQuery();
  const uploadMutation = useUploadExperimentalDatasetMutation();

  const [datasetKind, setDatasetKind] = useState<ExperimentalDatasetTemplateKind>("llm-model-baseline");
  const [file, setFile] = useState<File | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [lastOkReport, setLastOkReport] = useState<ExperimentalDatasetValidationReportDto | null>(null);
  const [lastFailedReport, setLastFailedReport] = useState<ExperimentalDatasetValidationReportDto | null>(null);

  const sortedKinds = useMemo(() => [...EXPERIMENTAL_DATASET_TEMPLATE_KINDS], []);

  async function onDownloadTemplate(kind: ExperimentalDatasetTemplateKind) {
    const blob = await downloadExperimentalDatasetTemplate(kind);
    triggerBrowserBlobDownload(blob, suggestedTemplateFilename(kind));
  }

  async function onUpload() {
    if (!file) {
      return;
    }
    uploadMutation.reset();
    setLastOkReport(null);
    setLastFailedReport(null);
    try {
      const outcome = await uploadMutation.mutateAsync({
        file,
        datasetType: datasetKind,
        name: name || undefined,
        description: description || undefined,
      });
      if (outcome.ok) {
        setLastOkReport(outcome.data.validationReport);
      } else {
        setLastFailedReport(outcome.failed.validationReport);
      }
    } catch (e) {
      void e;
    }
  }

  const uploadErr =
    uploadMutation.isError && uploadMutation.error
      ? getSafeApiErrorMessage(uploadMutation.error)
      : "";

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
        <div className="min-w-0 flex-1 space-y-1.5">
          <CardTitle className="text-base">{t("experimentalDatasetTitle")}</CardTitle>
          <CardDescription>{t("experimentalDatasetDescription")}</CardDescription>
        </div>
        <HelpPopover
          triggerAriaLabel={tHelp("labExperimentalDatasetTriggerLabel")}
          title={tHelp("labExperimentalDatasetTitle")}
          message={tHelp("labExperimentalDatasetMessage")}
          details={tHelp("labExperimentalDatasetDetails")}
        />
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="space-y-2">
          <Label>{t("experimentalDatasetTemplatesTitle")}</Label>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              data-testid="lab-template-llm"
              onClick={() => void onDownloadTemplate("llm-model-baseline")}
            >
              {t("templateDownloadLlmBaseline")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              data-testid="lab-template-embedding"
              onClick={() => void onDownloadTemplate("embedding-baseline")}
            >
              {t("templateDownloadEmbeddingBaseline")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              data-testid="lab-template-rag"
              onClick={() => void onDownloadTemplate("rag-preset-benchmark")}
            >
              {t("templateDownloadRagPreset")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              data-testid="lab-template-classifier"
              onClick={() => void onDownloadTemplate("classifier-question-querytype")}
            >
              {t("templateDownloadClassifier")}
            </Button>
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="lab-exp-dataset-kind">{t("experimentalDatasetKindLabel")}</Label>
          <select
            id="lab-exp-dataset-kind"
            className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
            value={datasetKind}
            onChange={(e) => setDatasetKind(e.target.value as ExperimentalDatasetTemplateKind)}
          >
            {sortedKinds.map((k) => (
              <option key={k} value={k}>
                {t(`experimentalDatasetKind.${k}`)}
              </option>
            ))}
          </select>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="lab-exp-dataset-file">{t("experimentalDatasetFileLabel")}</Label>
            <Input
              id="lab-exp-dataset-file"
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lab-exp-dataset-name">{t("experimentalDatasetNameOptional")}</Label>
            <Input
              id="lab-exp-dataset-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t("experimentalDatasetNamePlaceholder")}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lab-exp-dataset-desc">{t("experimentalDatasetDescOptional")}</Label>
            <Input
              id="lab-exp-dataset-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t("experimentalDatasetDescPlaceholder")}
            />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" disabled={!file || uploadMutation.isPending} onClick={() => void onUpload()}>
            {uploadMutation.isPending ? t("experimentalDatasetUploading") : t("experimentalDatasetUpload")}
          </Button>
          {!file ? <span className="text-muted-foreground text-xs">{t("experimentalDatasetFileRequired")}</span> : null}
        </div>

        {uploadErr ? (
          <p className="text-destructive text-sm" role="alert">
            {uploadErr}
          </p>
        ) : null}

        {lastOkReport ? (
          <ValidationReportBlock
            report={lastOkReport}
            title={t("experimentalDatasetValidationOkTitle")}
            t={t}
          />
        ) : null}

        {lastFailedReport ? (
          <ValidationReportBlock
            report={lastFailedReport}
            title={t("experimentalDatasetValidationFailedTitle")}
            t={t}
          />
        ) : null}

        <div className="space-y-2 border-t pt-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-sm font-medium">{t("experimentalDatasetListTitle")}</h3>
            <button
              type="button"
              className="text-primary text-xs underline-offset-4 hover:underline"
              onClick={() => void refetch()}
            >
              {t("experimentalDatasetListRefresh")}
            </button>
          </div>
          {isError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("experimentalDatasetListError")}
            </p>
          ) : null}
          {isLoading ? <p className="text-muted-foreground text-xs">{t("experimentalDatasetListLoading")}</p> : null}
          {datasets && datasets.length === 0 ? (
            <p className="text-muted-foreground text-xs">{t("experimentalDatasetListEmpty")}</p>
          ) : null}
          {datasets && datasets.length > 0 ? (
            <ul className="space-y-2 text-xs">
              {datasets.map((row) => (
                <li key={row.id} className="bg-muted/40 rounded-md border px-3 py-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono">{row.id.slice(0, 8)}…</span>
                    <span>{row.name ?? t("experimentalDatasetUnnamed")}</span>
                    {row.readOnly ? (
                      <Badge variant="outline">{t("experimentalDatasetReadOnly")}</Badge>
                    ) : null}
                    {row.validationStatus === "VALID" ? (
                      <Badge variant="default">{t("experimentalDatasetValidationStatusValid")}</Badge>
                    ) : row.validationStatus === "INVALID" ? (
                      <Badge variant="destructive">{t("experimentalDatasetValidationStatusInvalid")}</Badge>
                    ) : row.validationStatus ? (
                      <Badge variant="secondary">{row.validationStatus}</Badge>
                    ) : (
                      <Badge variant="outline">{t("experimentalDatasetValidationStatusUnknown")}</Badge>
                    )}
                  </div>
                  <div className="text-muted-foreground mt-1">
                    {row.experimentalDatasetType}
                    {row.questionCount != null ? ` · ${t("experimentalDatasetRowCounts", { q: row.questionCount, r: row.rowCount ?? row.questionCount })}` : null}
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
