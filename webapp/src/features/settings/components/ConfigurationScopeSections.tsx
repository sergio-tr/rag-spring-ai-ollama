"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";

type SettingsCollapsibleSectionProps = Readonly<{
  title: string;
  description?: ReactNode;
  children: ReactNode;
  defaultOpen?: boolean;
  testId: string;
}>;

export function SettingsCollapsibleSection({
  title,
  description,
  children,
  defaultOpen = true,
  testId,
}: SettingsCollapsibleSectionProps) {
  return (
    <details
      open={defaultOpen}
      className="min-w-0 max-w-full overflow-hidden rounded-md border border-border p-3"
      data-testid={testId}
    >
      <summary className="cursor-pointer text-sm font-medium">{title}</summary>
      {description ? (
        <p className="text-muted-foreground mt-2 break-words text-xs">{description}</p>
      ) : null}
      <div className="mt-3 flex min-w-0 max-w-full flex-col gap-4">{children}</div>
    </details>
  );
}

type ConfigurationModelSectionProps = Readonly<{
  children: ReactNode;
  description?: ReactNode;
}>;

export function ConfigurationModelSection({ children, description }: ConfigurationModelSectionProps) {
  const t = useTranslations("Settings");

  return (
    <section
      className="flex min-w-0 max-w-full flex-col gap-4"
      data-testid="settings-model-configuration-section"
    >
      <div className="min-w-0">
        <h3 className="text-sm font-medium">{t("settingsSectionModelConfiguration")}</h3>
        {description ? (
          <p className="text-muted-foreground mt-1 break-words text-xs">{description}</p>
        ) : null}
      </div>
      {children}
    </section>
  );
}

type ConfigurationPromptSectionProps = Readonly<{
  children: ReactNode;
  description?: ReactNode;
}>;

export function ConfigurationPromptSection({ children, description }: ConfigurationPromptSectionProps) {
  const t = useTranslations("Settings");

  return (
    <section
      className="flex min-w-0 max-w-full flex-col gap-4 border-t pt-4"
      data-testid="settings-prompt-configuration-section"
    >
      <div className="min-w-0">
        <h3 className="text-sm font-medium">{t("settingsSectionPromptConfiguration")}</h3>
        {description ? (
          <p className="text-muted-foreground mt-1 break-words text-xs">{description}</p>
        ) : null}
      </div>
      {children}
    </section>
  );
}

type SettingsPreviewConfigurationSectionProps = Readonly<{
  children: ReactNode;
  description?: ReactNode;
}>;

/** Project-only outer wrapper for scoped model and prompt overrides. */
export function SettingsPreviewConfigurationSection({
  children,
  description,
}: SettingsPreviewConfigurationSectionProps) {
  const t = useTranslations("Settings");

  return (
    <section
      className="flex min-w-0 max-w-full flex-col gap-6 border-t pt-4"
      data-testid="settings-preview-configuration"
    >
      <div className="min-w-0">
        <h3 className="text-sm font-medium">{t("settingsPreviewConfigurationTitle")}</h3>
        {description ? (
          <p className="text-muted-foreground mt-1 break-words text-xs">{description}</p>
        ) : null}
      </div>
      {children}
    </section>
  );
}
