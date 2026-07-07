"use client";

import { CircleHelp } from "lucide-react";
import type { ReactNode } from "react";
import { buttonVariants } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle, PopoverTrigger } from "@/components/ui/popover";
import { Link } from "@/navigation";
import { cn } from "@/lib/utils";

export type HelpPopoverProps = Readonly<{
  /** Accessible name for the help trigger (required - do not rely on hover). */
  triggerAriaLabel: string;
  title: string;
  message: string;
  details?: string;
  /** Optional footer slot (e.g. extra links). */
  footer?: ReactNode;
  /** Locale-neutral path for internal navigation. */
  learnMoreHref?: string;
  learnMoreLabel?: string;
  triggerClassName?: string;
}>;

/**
 * Small contextual help disclosure (popover). Keyboard: focus trigger, Enter/Space opens; Escape closes (Base UI).
 */
export function HelpPopover({
  triggerAriaLabel,
  title,
  message,
  details,
  footer,
  learnMoreHref,
  learnMoreLabel,
  triggerClassName,
}: HelpPopoverProps) {
  return (
    <Popover>
      <PopoverTrigger
        type="button"
        className={cn(
          buttonVariants({ variant: "ghost", size: "icon-sm" }),
          "shrink-0 text-muted-foreground",
          triggerClassName,
        )}
        aria-label={triggerAriaLabel}
      >
        <CircleHelp className="size-4" aria-hidden />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(100vw-2rem,20rem)] p-4">
        <PopoverTitle className="mb-2">{title}</PopoverTitle>
        <PopoverDescription className="text-foreground">{message}</PopoverDescription>
        {details ? (
          <p className="text-muted-foreground mt-2 text-xs leading-relaxed">{details}</p>
        ) : null}
        {learnMoreHref && learnMoreLabel ? (
          <p className="mt-3">
            <Link href={learnMoreHref} className="text-primary text-sm font-medium underline-offset-4 hover:underline">
              {learnMoreLabel}
            </Link>
          </p>
        ) : null}
        {footer}
      </PopoverContent>
    </Popover>
  );
}
