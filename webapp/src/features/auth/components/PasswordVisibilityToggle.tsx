"use client";

import { Eye, EyeOff } from "lucide-react";

type Props = {
  /** When true, password characters are visible (text input). */
  visible: boolean;
  onToggle: () => void;
  /** Accessible name when password is hidden (action: reveal). */
  showPasswordLabel: string;
  /** Accessible name when password is visible (action: conceal). */
  hidePasswordLabel: string;
};

const toggleClassName =
  "inline-flex h-9 shrink-0 items-center justify-center rounded-md border border-input bg-background px-2 text-muted-foreground outline-none hover:bg-accent hover:text-accent-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50";

/**
 * Icon-only password visibility control (lucide-react). Uses aria-pressed for toggle state.
 */
export function PasswordVisibilityToggle(props: Readonly<Props>) {
  const { visible, onToggle, showPasswordLabel, hidePasswordLabel } = props;

  return (
    <button
      type="button"
      className={toggleClassName}
      aria-label={visible ? hidePasswordLabel : showPasswordLabel}
      aria-pressed={visible}
      onClick={onToggle}
    >
      {visible ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
    </button>
  );
}
