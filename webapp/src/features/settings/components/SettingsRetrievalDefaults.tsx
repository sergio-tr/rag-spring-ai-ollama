"use client";

import type { UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";
import { labelConfigField } from "@/features/settings/lib/config-field-copy";
import { isFieldInherited } from "@/features/settings/lib/effective-config-form-values";

type SettingsRetrievalDefaultsProps = Readonly<{
  form: UseFormReturn<ConfigFormValues>;
  fields: ConfigSchemaField[];
  storedOverrides?: Record<string, unknown>;
  mode?: "user" | "project";
}>;

export function SettingsRetrievalDefaults({ form, fields, storedOverrides, mode = "user" }: SettingsRetrievalDefaultsProps) {
  const t = useTranslations("Settings");

  if (fields.length === 0) {
    return null;
  }

  return (
    <div className="space-y-3" data-testid="settings-retrieval-defaults">
      <p className="text-muted-foreground break-words text-xs">{t("settingsRetrievalDefaultsDescription")}</p>
      <ConfigSchemaFieldRows
        fields={fields}
        form={form}
        labelFor={(key) => {
          const base = labelConfigField(key, (translationKey) => t(translationKey as never));
          if (mode === "project" && isFieldInherited(storedOverrides, key)) {
            return `${base} (${t("settingsRetrievalInheritedLabel")})`;
          }
          return base;
        }}
        inputIdPrefix="cfg"
      />
    </div>
  );
}
