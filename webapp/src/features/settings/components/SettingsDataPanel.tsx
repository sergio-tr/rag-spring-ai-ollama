"use client";

import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useState } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { MeDocumentsPageResponse, MeSummaryResponse } from "@/types/api";

export const settingsDataQueryKeys = {
  summary: ["settings", "me", "summary"] as const,
  documents: (page: number, size: number) =>
    ["settings", "me", "documents", page, size] as const,
};

export function SettingsDataPanel() {
  const t = useTranslations("Settings");
  const [technicalReferenceOpen, setTechnicalReferenceOpen] = useState(false);

  const summaryQ = useQuery({
    queryKey: settingsDataQueryKeys.summary,
    queryFn: () => apiFetch<MeSummaryResponse>(apiProductPath("/me/summary")),
  });
  const docsQ = useQuery({
    queryKey: settingsDataQueryKeys.documents(0, 50),
    queryFn: () => {
      const q = new URLSearchParams({ page: "0", size: "50" });
      return apiFetch<MeDocumentsPageResponse>(apiProductPath(`/me/documents?${q}`));
    },
  });

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{t("dataSummaryTitle")}</CardTitle>
          <CardDescription data-testid="data-summary-description">
            {t("dataSummaryDescription")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {summaryQ.isLoading && (
            <p className="text-muted-foreground text-sm">{t("dataLoading")}</p>
          )}
          {summaryQ.isError && (
            <p className="text-destructive text-sm" role="alert">
              {t("dataSummaryError")}
            </p>
          )}
          {summaryQ.data && (
            <dl className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
              <div>
                <dt className="text-muted-foreground">{t("dataProjects")}</dt>
                <dd className="font-medium">{summaryQ.data.projectCount}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{t("dataConversations")}</dt>
                <dd className="font-medium">{summaryQ.data.conversationCount}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{t("dataDocuments")}</dt>
                <dd className="font-medium">{summaryQ.data.documentCount}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{t("dataBytes")}</dt>
                <dd className="font-medium">{summaryQ.data.estimatedStorageBytes}</dd>
              </div>
            </dl>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>{t("dataInventoryTitle")}</CardTitle>
          <CardDescription data-testid="data-inventory-description">
            {t("dataInventoryDescription")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {docsQ.isLoading && (
            <p className="text-muted-foreground text-sm">{t("dataLoading")}</p>
          )}
          {docsQ.isError && (
            <p className="text-destructive text-sm" role="alert">
              {t("dataInventoryError")}
            </p>
          )}
          {docsQ.data?.items.length === 0 && (
            <p className="text-muted-foreground text-sm" data-testid="data-inventory-empty">
              {t("dataInventoryEmpty")}
            </p>
          )}
          {docsQ.data && docsQ.data.items.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="py-2 pr-2 font-medium">{t("dataColFile")}</th>
                    <th className="py-2 pr-2 font-medium">{t("dataColScope")}</th>
                    <th className="py-2 pr-2 font-medium">{t("dataColStatus")}</th>
                    <th className="py-2 pr-2 font-medium">{t("dataColProject")}</th>
                  </tr>
                </thead>
                <tbody>
                  {docsQ.data.items.map((row) => (
                    <tr key={row.documentId} className="border-b border-border/60">
                      <td className="py-1.5 pr-2 align-top">{row.fileName}</td>
                      <td className="py-1.5 pr-2 align-top">{row.corpusScope}</td>
                      <td className="py-1.5 pr-2 align-top">{row.status}</td>
                      <td className="py-1.5 pr-2 align-top font-mono text-xs">
                        {row.projectId}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="text-muted-foreground mt-2 text-xs">
                {t("dataTotal", { count: docsQ.data.total })}
              </p>
            </div>
          )}
        </CardContent>
      </Card>
      <details
        className="rounded-lg border border-border px-4 py-3"
        data-testid="data-technical-reference"
        onToggle={(e) => {
          setTechnicalReferenceOpen((e.target as HTMLDetailsElement).open);
        }}
      >
        <summary className="cursor-pointer text-sm font-medium text-foreground">
          {t("dataAdvancedSummary")}
        </summary>
        {technicalReferenceOpen ? (
          <p className="text-muted-foreground mt-3 text-sm">{t("dataAdvancedDetails")}</p>
        ) : null}
      </details>
    </div>
  );
}
