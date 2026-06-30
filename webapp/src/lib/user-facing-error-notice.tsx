"use client";

import {
  resolveUserFacingErrorDisplay,
  type LlmProviderKind,
} from "@/lib/user-facing-error-messages";

export type UserFacingErrorNoticeProps = Readonly<{
  raw: string | null | undefined;
  fallback: string;
  t: (key: string) => string;
  provider?: LlmProviderKind | null;
  explicitCode?: string | null;
  testId?: string;
  className?: string;
  technicalSummary?: string;
}>;

/** Human error in the main surface; raw codes and validator text stay in collapsed details. */
export function UserFacingErrorNotice({
  raw,
  fallback,
  t,
  provider,
  explicitCode,
  testId,
  className,
  technicalSummary,
}: UserFacingErrorNoticeProps) {
  const display = resolveUserFacingErrorDisplay({ raw, t, fallback, provider, explicitCode });
  const detailsLabel = technicalSummary ?? t("userErrorTechnicalDetailsSummary");

  return (
    <div className={className} data-testid={testId}>
      <p className="text-destructive text-sm" role="alert" data-testid={testId ? `${testId}-primary` : "user-facing-error-primary"}>
        {display.primary}
      </p>
      {display.action ? (
        <p className="text-muted-foreground mt-1 text-xs" data-testid={testId ? `${testId}-action` : "user-facing-error-action"}>
          {display.action}
        </p>
      ) : null}
      {display.technical ? (
        <details className="mt-2 text-xs" data-testid="user-facing-error-technical">
          <summary className="cursor-pointer text-muted-foreground">{detailsLabel}</summary>
          <pre
            className="text-muted-foreground mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded border bg-muted/30 p-2 font-mono text-[10px]"
            data-testid="user-facing-error-technical-code"
          >
            {display.technical}
          </pre>
        </details>
      ) : null}
    </div>
  );
}
