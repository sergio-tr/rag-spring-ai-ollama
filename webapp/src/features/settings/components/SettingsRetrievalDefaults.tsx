"use client";

import type { UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { ConfigSchemaFieldRows } from "@/features/settings/components/config-schema-field-rows";
import { labelConfigField } from "@/features/settings/lib/config-field-copy";

type SettingsRetrievalDefaultsProps = Readonly<{
  form: UseFormReturn<ConfigFormValues>;
  fields: ConfigSchemaField[];
}>;

export function SettingsRetrievalDefaults({ form, fields }: SettingsRetrievalDefaultsProps) {
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
        labelFor={(key) => labelConfigField(key, (translationKey) => t(translationKey as never))}
        inputIdPrefix="cfg"
      />
    </div>
  );
}
