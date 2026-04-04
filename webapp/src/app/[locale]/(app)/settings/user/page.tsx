"use client";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { MeCanonicalJsonPanels } from "@/features/settings/components/MeCanonicalJsonPanels";
import { RagConfigForm } from "@/features/settings/components/RagConfigForm";
import { useTranslations } from "next-intl";

export default function SettingsUserConfigPage() {
  const t = useTranslations("Settings");

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{t("userConfigTitle")}</CardTitle>
          <CardDescription>{t("userConfigDescription")}</CardDescription>
        </CardHeader>
        <CardContent>
          <RagConfigForm mode="user" />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>{t("meCanonicalCardTitle")}</CardTitle>
          <CardDescription>{t("meCanonicalCardDescription")}</CardDescription>
        </CardHeader>
        <CardContent>
          <MeCanonicalJsonPanels />
        </CardContent>
      </Card>
    </div>
  );
}
