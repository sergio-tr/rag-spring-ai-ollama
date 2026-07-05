"use client";

import type { ReactNode } from "react";
import {
  shouldShowDraftError,
  type NumericDraft,
} from "@/features/chat/lib/retrieval-numeric-draft";

export type RetrievalNumericFieldProps = Readonly<{
  testId: string;
  label: ReactNode;
  draft: NumericDraft;
  focused: boolean;
  touched: boolean;
  disabled?: boolean;
  inputMode: "numeric" | "decimal";
  errorMessages: {
    invalid: string;
    range: string;
    required: string;
  };
  sourceHint?: ReactNode;
  onFocus: () => void;
  onBlur: () => void;
  onDraftChange: (text: string) => void;
}>;

function draftErrorMessage(
  draft: NumericDraft,
  messages: RetrievalNumericFieldProps["errorMessages"],
): string | null {
  if (draft.error === "invalid") return messages.invalid;
  if (draft.error === "range") return messages.range;
  if (draft.error === "required") return messages.required;
  return null;
}

export function RetrievalNumericField({
  testId,
  label,
  draft,
  focused,
  touched,
  disabled,
  inputMode,
  errorMessages,
  sourceHint,
  onFocus,
  onBlur,
  onDraftChange,
}: RetrievalNumericFieldProps) {
  const showError = shouldShowDraftError(draft, { focused, touched });
  const errorMessage = showError ? draftErrorMessage(draft, errorMessages) : null;

  return (
    <label className="flex min-w-[220px] flex-1 flex-col gap-1 text-sm">
      {label}
      <input
        data-testid={testId}
        type="text"
        inputMode={inputMode}
        autoComplete="off"
        spellCheck={false}
        className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm aria-invalid:border-destructive"
        value={draft.text}
        disabled={disabled}
        aria-invalid={showError}
        onFocus={onFocus}
        onBlur={onBlur}
        onChange={(event) => onDraftChange(event.target.value)}
      />
      {errorMessage ? (
        <span className="text-destructive text-[11px]" role="alert" data-testid={`${testId}-error`}>
          {errorMessage}
        </span>
      ) : null}
      {sourceHint}
    </label>
  );
}
