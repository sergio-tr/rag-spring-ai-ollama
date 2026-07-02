"use client";

import type { ReactNode } from "react";

export type TechnicalDetailsProps = Readonly<{
  summary: string;
  children: ReactNode;
  testId?: string;
  defaultOpen?: boolean;
}>;

export function ChatConfigTechnicalDetails({
  summary,
  children,
  testId,
  defaultOpen = false,
}: TechnicalDetailsProps) {
  return (
    <details
      className="rounded-lg border bg-muted/20 p-3 text-xs"
      data-testid={testId ?? "chat-config-technical-details"}
      open={defaultOpen ? true : undefined}
    >
      <summary className="cursor-pointer font-medium text-foreground">{summary}</summary>
      <div className="mt-3 space-y-3">{children}</div>
    </details>
  );
}

export type CompactSummaryRowProps = Readonly<{
  label: string;
  value: ReactNode;
  testId?: string;
}>;

export function CompactSummaryRow({ label, value, testId }: CompactSummaryRowProps) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-3 text-sm" data-testid={testId}>
      <span className="text-muted-foreground shrink-0">{label}</span>
      <span className="min-w-0 break-words sm:text-right font-medium leading-snug [overflow-wrap:anywhere]">
        {value}
      </span>
    </div>
  );
}
