"use client";

import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { CompactHelp } from "@/features/lab/components/compact-lab-ui";
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
    <div className="flex flex-col gap-4" data-testid="lab-classifier-page">
      <Card>
        <CardHeader className="gap-1 pb-2">
          <CardTitle className="text-lg">{t("classifierTitle")}</CardTitle>
          <p className="text-muted-foreground text-xs">{t("classifierTagline")}</p>
        </CardHeader>
      </Card>

      {classifierOk ? null : (
        <output className="text-amber-600 block text-sm dark:text-amber-500">
          {t("classifierNotConfiguredWarn")}
        </output>
      )}

      <CompactHelp summary={t("adrDisclaimerSummary")} testId="lab-classifier-disclaimer-help">
        <p className="text-muted-foreground text-xs leading-relaxed">{t("adrDisclaimer")}</p>
      </CompactHelp>

      <ClassifierRegistrySection />

      <LabClassifierTrainPanel classifierOk={classifierOk} projectId={projectId} />
      <LabClassifierEvalPanel classifierOk={classifierOk} projectId={projectId} />
      <LabClassifierClassifyPanel classifierOk={classifierOk} />
    </div>
  );
}
