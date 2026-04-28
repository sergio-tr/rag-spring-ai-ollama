"use client";

import { DocumentUploadZone } from "@/features/documents/components/DocumentUploadZone";
import { StatusBadge } from "@/features/documents/components/StatusBadge";
import {
  useDeleteProjectDocument,
  useProjectDocuments,
} from "@/features/documents/hooks/use-project-documents";
import { useAppStore } from "@/store/app.store";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useEffect, useMemo } from "react";
import { Button } from "@/components/ui/button";

export default function DocumentsPage() {
  const t = useTranslations("Documents");
  const active = useAppStore((s) => s.activeProject);
  const projectId = active?.id;
  const qc = useQueryClient();
  const { data, isLoading, isError } = useProjectDocuments(projectId);
  const del = useDeleteProjectDocument(projectId);
  const rows = data ?? [];

  const hasIngesting = useMemo(
    () => rows.some((d) => d.status === "INGESTING"),
    [rows],
  );

  useEffect(() => {
    if (!projectId || !hasIngesting) return;
    const id = globalThis.setInterval(() => {
      void qc.invalidateQueries({ queryKey: ["project-documents", projectId] });
    }, 2000);
    return () => globalThis.clearInterval(id);
  }, [hasIngesting, projectId, qc]);

  if (!projectId) {
    return <p className="text-muted-foreground text-sm">{t("noActiveProject")}</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
      </div>
      <DocumentUploadZone projectId={projectId} />
      {isLoading && <p className="text-muted-foreground text-sm">{t("loading")}</p>}
      {isError && (
        <p className="text-destructive text-sm" role="alert">
          {t("loadError")}
        </p>
      )}
      {!isLoading && !isError && rows.length === 0 && (
        <p className="text-muted-foreground text-sm">{t("empty")}</p>
      )}
      {rows.length > 0 && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-left text-sm">
            <thead className="border-b bg-muted/40">
              <tr>
                <th className="p-3 font-medium">{t("colName")}</th>
                <th className="p-3 font-medium">{t("colStatus")}</th>
                <th className="p-3 font-medium">{t("colChunks")}</th>
                <th className="p-3 font-medium">{t("colUploaded")}</th>
                <th className="p-3 font-medium">{t("colActions")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-border/80">
                  <td className="p-3">{row.fileName}</td>
                  <td className="p-3">
                    <StatusBadge status={row.status} />
                  </td>
                  <td className="p-3">{row.chunkCount ?? "—"}</td>
                  <td className="p-3 text-muted-foreground">
                    {new Date(row.uploadedAt).toLocaleString()}
                  </td>
                  <td className="p-3">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={del.isPending}
                      onClick={() => del.mutate(row.id)}
                    >
                      {t("delete")}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
