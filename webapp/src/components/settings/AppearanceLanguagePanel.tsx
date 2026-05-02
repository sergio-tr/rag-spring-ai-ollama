"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { usePathname, useRouter } from "@/navigation";
import type { AppLocale } from "@/i18n/routing";
import { Button } from "@/components/ui/button";
import { HelpPopover } from "@/features/help/HelpPopover";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import { cn } from "@/lib/utils";

export function AppearanceLanguagePanel({ className }: { className?: string }) {
  const t = useTranslations("Theme");
  const tSettings = useTranslations("Settings");
  const tHelp = useTranslations("Help");
  const { theme, setTheme } = useTheme();
  const locale = useLocale() as AppLocale;
  const router = useRouter();
  const pathname = usePathname();

  function switchLocale(next: AppLocale) {
    router.replace(pathname, { locale: next });
  }

  return (
    <div className={cn("flex flex-col gap-8", className)}>
      <section aria-labelledby="settings-theme-heading">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <h2 id="settings-theme-heading" className="font-medium text-sm">
            {tSettings("themeSection")}
          </h2>
          <HelpPopover
            triggerAriaLabel={tHelp("settingsAppearanceTriggerLabel")}
            title={tHelp("settingsAppearanceTitle")}
            message={tHelp("settingsAppearanceMessage")}
            details={tHelp("settingsAppearanceDetails")}
            learnMoreHref="/settings"
            learnMoreLabel={tHelp("settingsAppearanceLearnMore")}
          />
        </div>
        <InlineHelpStatus status="info" label={tHelp("settingsAppearanceInline")} className="mb-3" />
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant={theme === "light" ? "default" : "outline"}
            size="sm"
            onClick={() => setTheme("light")}
          >
            <Sun className="mr-2 size-4" aria-hidden />
            {t("light")}
          </Button>
          <Button
            type="button"
            variant={theme === "dark" ? "default" : "outline"}
            size="sm"
            onClick={() => setTheme("dark")}
          >
            <Moon className="mr-2 size-4" aria-hidden />
            {t("dark")}
          </Button>
          <Button
            type="button"
            variant={theme === "system" ? "default" : "outline"}
            size="sm"
            onClick={() => setTheme("system")}
          >
            <Monitor className="mr-2 size-4" aria-hidden />
            {t("system")}
          </Button>
        </div>
      </section>
      <section aria-labelledby="settings-lang-heading">
        <h2 id="settings-lang-heading" className="mb-3 font-medium text-sm">
          {tSettings("languageSection")}
        </h2>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant={locale === "en" ? "default" : "outline"}
            size="sm"
            onClick={() => switchLocale("en")}
          >
            English
          </Button>
          <Button
            type="button"
            variant={locale === "es" ? "default" : "outline"}
            size="sm"
            onClick={() => switchLocale("es")}
          >
            Español
          </Button>
        </div>
      </section>
    </div>
  );
}
