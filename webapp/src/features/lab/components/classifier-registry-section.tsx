"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { HelpPopover } from "@/features/help/HelpPopover";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useActivateClassifierModel, useClassifierModelsQuery } from "@/features/lab/hooks/use-classifier-registry";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useAppStore } from "@/store/app.store";
import type { ClassifierModelRegistryEntryDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useState } from "react";

export function ClassifierRegistrySection() {
  const t = useTranslations("Lab");
  const tHelp = useTranslations("Help");
  const { data: labStatus } = useLabStatus();
  const activeProject = useAppStore((s) => s.activeProject);
  const classifierOk = labStatus?.classifier.configured ?? false;
  const { data: models, isLoading, error, refetch } = useClassifierModelsQuery(classifierOk);
  const activate = useActivateClassifierModel();
  const [confirmModel, setConfirmModel] = useState<ClassifierModelRegistryEntryDto | null>(null);

  if (!classifierOk) {
    return null;
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
          <div className="min-w-0 flex-1 space-y-1.5">
            <CardTitle>{t("registryTitle")}</CardTitle>
            <CardDescription>{t("registryDescription")}</CardDescription>
          </div>
          <HelpPopover
            triggerAriaLabel={tHelp("registryHelpTriggerLabel")}
            title={tHelp("registryHelpTitle")}
            message={tHelp("registryHelpMessage")}
            details={tHelp("registryHelpDetails")}
          />
        </CardHeader>
        <CardContent className="space-y-3">
          {error && (
            <p className="text-destructive text-sm" role="alert">
              {t("registryLoadError")}
            </p>
          )}
          {isLoading && <p className="text-muted-foreground text-sm">{t("registryLoading")}</p>}
          {!isLoading && !error && models?.length === 0 && (
            <p className="text-muted-foreground text-sm">{t("registryEmpty")}</p>
          )}
          {models && models.length > 0 && (
            <div className="overflow-x-auto rounded-md border" data-testid="classifier-registry-table">
              <table className="w-full text-left text-xs">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="p-2 font-medium">{t("registryColName")}</th>
                    <th className="p-2 font-medium">{t("registryColTag")}</th>
                    <th className="p-2 font-medium">{t("registryColStatus")}</th>
                    <th className="p-2 font-medium">{t("registryColMetrics")}</th>
                    <th className="p-2 font-medium">{t("registryColActive")}</th>
                    <th className="p-2 font-medium" />
                  </tr>
                </thead>
                <tbody>
                  {models.map((m) => (
                    <tr key={m.id} className="border-t">
                      <td className="p-2">{m.name}</td>
                      <td className="p-2">
                        <code className="break-all">{m.inferenceTag}</code>
                      </td>
                      <td className="p-2">{m.status}</td>
                      <td className="p-2 text-muted-foreground">
                        {m.accuracy != null ? `acc ${m.accuracy.toFixed(4)}` : "—"}
                        {m.f1Macro != null ? ` · f1 ${m.f1Macro.toFixed(4)}` : ""}
                      </td>
                      <td className="p-2">
                        {m.active ? <Badge>{t("registryBadgeActive")}</Badge> : "—"}
                      </td>
                      <td className="p-2">
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          disabled={!activeProject || m.status !== "READY" || activate.isPending}
                          onClick={() => setConfirmModel(m)}
                        >
                          {t("registryActivate")}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Button type="button" variant="outline" size="sm" onClick={() => void refetch()}>
            {t("registryRefresh")}
          </Button>
        </CardContent>
      </Card>

      <Dialog open={confirmModel != null} onOpenChange={(o) => !o && setConfirmModel(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("registryConfirmTitle")}</DialogTitle>
            <DialogDescription>
              {activeProject
                ? t("registryConfirmBody", {
                    project: activeProject.name,
                    tag: confirmModel?.inferenceTag ?? "",
                  })
                : t("registryNoProject")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button type="button" variant="outline" onClick={() => setConfirmModel(null)}>
              {t("registryCancel")}
            </Button>
            <Button
              type="button"
              disabled={!activeProject || !confirmModel || activate.isPending}
              onClick={() => {
                if (!activeProject || !confirmModel) return;
                void activate
                  .mutateAsync({
                    modelId: confirmModel.id,
                    body: { projectId: activeProject.id },
                  })
                  .then(() => setConfirmModel(null))
                  .catch(() => {
                    /* error surfaced via mutation if needed */
                  });
              }}
            >
              {t("registryConfirmActivate")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
