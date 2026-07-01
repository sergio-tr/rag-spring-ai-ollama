"use client";

import { RagConfigForm } from "@/features/settings/components/RagConfigForm";

export default function SettingsUserConfigPage() {
  return (
    <div className="flex flex-col gap-6">
      <RagConfigForm mode="user" />
    </div>
  );
}
