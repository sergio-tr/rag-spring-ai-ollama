"use client";

import { AppearanceLanguagePanel } from "@/components/settings/AppearanceLanguagePanel";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ProductModelRegistryCard } from "@/features/settings/components/ProductModelRegistryCard";
import { useTranslations } from "next-intl";

export default function SettingsPage() {
  const t = useTranslations("Settings");

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{t("preferencesCardTitle")}</CardTitle>
          <CardDescription>{t("preferencesCardDescription")}</CardDescription>
        </CardHeader>
        <CardContent>
          <AppearanceLanguagePanel />
        </CardContent>
      </Card>
      <ProductModelRegistryCard />
    </div>
  );
}
