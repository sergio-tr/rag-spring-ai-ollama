"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { HelpPopover } from "@/features/help/HelpPopover";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useTranslations } from "next-intl";

export default function LabOverviewPage() {
  const t = useTranslations("Lab");
  const tHelp = useTranslations("Help");
  const { data: status, isError, isLoading, refetch } = useLabStatus();

  return (
    <div className="space-y-6">
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
              {t("statusError")}
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
                    <div className="flex items-center gap-2">
                      <Badge variant={status.datasets.enabled ? "default" : "secondary"}>
                        {status.datasets.enabled ? t("statusOn") : t("statusOff")}
                      </Badge>
                      <span className="text-muted-foreground">
                        {t("statusQuestionCount", { count: status.datasets.questionCount })}
                      </span>
                    </div>
                    {!status.datasets.enabled ? (
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

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
          <div className="min-w-0 flex-1 space-y-1.5">
            <CardTitle className="text-base">{t("obsTitle")}</CardTitle>
            <CardDescription>{t("obsDescription")}</CardDescription>
          </div>
          <HelpPopover
            triggerAriaLabel={tHelp("labObservabilityTriggerLabel")}
            title={tHelp("labObservabilityTitle")}
            message={tHelp("labObservabilityMessage")}
            details={tHelp("labObservabilityDetails")}
          />
        </CardHeader>
      </Card>
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
