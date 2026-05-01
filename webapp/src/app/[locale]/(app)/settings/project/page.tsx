"use client";

import { RagConfigForm } from "@/features/settings/components/RagConfigForm";
import { useAppStore } from "@/store/app.store";

export default function SettingsProjectConfigPage() {
  const active = useAppStore((s) => s.activeProject);
  return <RagConfigForm mode="project" projectId={active?.id} />;
}
