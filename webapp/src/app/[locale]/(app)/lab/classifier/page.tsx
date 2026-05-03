"use client";

import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ClassifierRegistrySection } from "@/features/lab/components/classifier-registry-section";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useAppStore } from "@/store/app.store";
import { useTranslations } from "next-intl";
import {
  LabClassifierClassifyPanel,
  LabClassifierEvalPanel,
  LabClassifierTrainPanel,
} from "./lab-classifier-panels";

export default function LabClassifierPage() {
  const t = useTranslations("Lab");
  const { data: labStatus } = useLabStatus();
  const activeProject = useAppStore((s) => s.activeProject);
  const classifierOk = labStatus?.classifier.configured ?? false;
  const projectId = activeProject?.id;

  return (
    <div className="flex flex-col gap-6">
      <p className="text-muted-foreground border-l-4 border-primary/40 pl-3 text-sm">{t("adrDisclaimer")}</p>
      {classifierOk ? null : (
        <output className="text-amber-600 block text-sm dark:text-amber-500">{t("classifierNotConfiguredWarn")}</output>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{t("classifierTitle")}</CardTitle>
          <CardDescription>{t("classifierDescription")}</CardDescription>
        </CardHeader>
      </Card>

      <ClassifierRegistrySection />

      <LabClassifierTrainPanel classifierOk={classifierOk} projectId={projectId} />
      <LabClassifierEvalPanel classifierOk={classifierOk} projectId={projectId} />
      <LabClassifierClassifyPanel classifierOk={classifierOk} />
    </div>
  );
}
