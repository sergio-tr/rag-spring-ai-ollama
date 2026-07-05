"use client";

import { RagConfigForm } from "@/features/settings/components/RagConfigForm";

export default function SettingsUserConfigPage() {
  return (
    <div className="flex min-w-0 max-w-full flex-col gap-6">
      <RagConfigForm mode="user" />
    </div>
  );
}
