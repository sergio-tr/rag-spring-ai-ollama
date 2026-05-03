"use client";

import { DocumentUploadZone } from "@/features/documents/components/DocumentUploadZone";
import { StatusBadge } from "@/features/documents/components/StatusBadge";
import {
  useDeleteProjectDocument,
  useProjectDocuments,
} from "@/features/documents/hooks/use-project-documents";
import { useSyncActiveProjectFromDocumentsUrl } from "@/features/projects/hooks/use-sync-active-project-from-documents-url";
import { buildProjectScopedDocumentsHref } from "@/features/projects/lib/open-project-navigation";
import { ApiError } from "@/lib/api-client";
import { useAppStore } from "@/store/app.store";
import { Link, useRouter } from "@/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo } from "react";
import { Button } from "@/components/ui/button";

function DocumentsPageFallback() {
  const t = useTranslations("Documents");
  return <p className="text-muted-foreground text-sm">{t("loading")}</p>;
}

export default function DocumentsPage() {
  return (
    <Suspense fallback={<DocumentsPageFallback />}>
      <DocumentsPageInner />
    </Suspense>
  );
}

function DocumentsPageInner() {
  const t = useTranslations("Documents");
  const router = useRouter();
  const searchParams = useSearchParams();
  const urlProjectId = searchParams?.get("projectId")?.trim() || null;
  useSyncActiveProjectFromDocumentsUrl(urlProjectId);

  const active = useAppStore((s) => s.activeProject);
  const projectId = active?.id;
  const qc = useQueryClient();
  const { data, isLoading, isError, error } = useProjectDocuments(projectId);
  const del = useDeleteProjectDocument(projectId);
  const rows = useMemo(() => data ?? [], [data]);

  useEffect(() => {
    if (!projectId || urlProjectId) return;
    router.replace(buildProjectScopedDocumentsHref(projectId));
  }, [projectId, urlProjectId, router]);

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
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
        {active?.name ? (
          <p className="text-muted-foreground text-xs">{t("scopedSubtitle", { name: active.name })}</p>
        ) : null}
      </div>
      <DocumentUploadZone projectId={projectId} />
      {isLoading && <p className="text-muted-foreground text-sm">{t("loading")}</p>}
      {isError && (
        <div
          role="alert"
          className="space-y-1 rounded-md border border-destructive/30 bg-destructive/5 p-3 text-destructive text-sm"
        >
          <p className="font-medium">{t("loadError")}</p>
          {error instanceof ApiError ? (
            <p className="text-muted-foreground text-xs">{error.message}</p>
          ) : null}
        </div>
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
