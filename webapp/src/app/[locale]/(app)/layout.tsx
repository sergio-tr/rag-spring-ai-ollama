"use client";

import { AppShell } from "@/components/layout/AppShell";
import { useTranslations } from "next-intl";

export default function AppGroupLayout({ children }: { children: React.ReactNode }) {
  const t = useTranslations("Panel");

  return <AppShell panelBody={t("body")}>{children}</AppShell>;
}
