"use client";

import type { LabDraftIssue } from "@/features/lab/lib/lab-draft-issues";
import { useTranslations } from "next-intl";

export type RagDraftIssuesAlertProps = {
  issues: LabDraftIssue[];
  testId?: string;
};

export function RagDraftIssuesAlert({ issues, testId = "lab-evaluation-draft-warnings" }: RagDraftIssuesAlertProps) {
  const t = useTranslations("Lab");

  if (issues.length === 0) {
    return null;
  }

  return (
    <output
      role="alert"
      data-testid={testId}
      className="block rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-amber-800 text-sm dark:text-amber-200"
    >
      <p className="font-medium">{t("evalDraftWarningsTitle")}</p>
      <ul className="mt-1 list-inside list-disc space-y-0.5" data-testid={`${testId}-list`}>
        {issues.map((item, index) => (
          <li key={`${item.code}-${index}`} data-testid={`${testId}-issue-${item.code}`}>
            <span>{t(item.messageKey, item.messageParams)}</span>
            {item.actionKey ? (
              <span className="text-muted-foreground"> {t(item.actionKey)}</span>
            ) : null}
          </li>
        ))}
      </ul>
    </output>
  );
}
