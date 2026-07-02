import { cn } from "@/lib/utils";

export type AssistantConfigScope = "account" | "project" | "conversation";

export function resolveFieldScope(
  fieldKey: string,
  manualOverrideKeys: readonly string[],
  options?: Readonly<{
    conversationModelKey?: string | null;
    conversationClassifierKey?: string | null;
  }>,
): AssistantConfigScope {
  if (manualOverrideKeys.includes(fieldKey)) {
    return "conversation";
  }
  if (fieldKey === "llmModel" && options?.conversationModelKey?.trim()) {
    return "conversation";
  }
  if (fieldKey === "classifierModelId" && options?.conversationClassifierKey?.trim()) {
    return "conversation";
  }
  return "project";
}

export function ConfigScopeBadge({
  scope,
  label,
  className,
}: Readonly<{
  scope: AssistantConfigScope;
  label: string;
  className?: string;
}>) {
  return (
    <span
      className={cn(
        "rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-muted-foreground shrink-0",
        className,
      )}
      data-testid={`config-scope-${scope}`}
    >
      {label}
    </span>
  );
}
