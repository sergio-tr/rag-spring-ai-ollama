"use client";

import { Languages, Monitor, Moon, Sun } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { usePathname, useRouter } from "@/navigation";
import type { AppLocale } from "@/i18n/routing";
import { cn } from "@/lib/utils";

export function ThemeLanguageMenu() {
  const t = useTranslations("Theme");
  const tNav = useTranslations("Nav");
  const tSettings = useTranslations("Settings");
  const { theme, setTheme } = useTheme();
  const locale = useLocale() as AppLocale;
  const router = useRouter();
  const pathname = usePathname();

  function switchLocale(next: AppLocale) {
    router.replace(pathname, { locale: next });
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        type="button"
        className={cn(buttonVariants({ variant: "outline", size: "sm" }), "gap-1.5")}
      >
        <Languages className="size-3.5" aria-hidden />
        {tNav("settings")}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-48">
        <DropdownMenuLabel>{tNav("settings")}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-muted-foreground text-xs font-normal">
          Theme
        </DropdownMenuLabel>
        <DropdownMenuItem onClick={() => setTheme("light")}>
          <Sun className="mr-2 size-4" />
          {t("light")}
          {theme === "light" ? " ✓" : ""}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("dark")}>
          <Moon className="mr-2 size-4" />
          {t("dark")}
          {theme === "dark" ? " ✓" : ""}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("system")}>
          <Monitor className="mr-2 size-4" />
          {t("system")}
          {theme === "system" ? " ✓" : ""}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-muted-foreground text-xs font-normal">
          {tSettings("languageSection")}
        </DropdownMenuLabel>
        <DropdownMenuItem onClick={() => switchLocale("en")}>
          English{locale === "en" ? " ✓" : ""}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => switchLocale("es")}>
          Español{locale === "es" ? " ✓" : ""}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
