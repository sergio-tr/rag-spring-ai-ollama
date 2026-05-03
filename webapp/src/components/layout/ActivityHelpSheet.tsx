"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Separator } from "@/components/ui/separator";
import { ExplainabilityPanel } from "@/features/rag/ExplainabilityPanel";
import { TraceHistoryList } from "@/features/trace/TraceHistoryList";

type ActivityHelpSheetProps = Readonly<{
  open: boolean;
  onOpenChange: (open: boolean) => void;
  isChatRoute: boolean;
  /** Short intro copy (e.g. translated Panel.body). */
  intro: ReactNode;
}>;

/**
 * Compact activity drawer replacing the wide detached tips aside (Phase 3B).
 */
export function ActivityHelpSheet({
  open,
  onOpenChange,
  isChatRoute,
  intro,
}: ActivityHelpSheetProps) {
  const t = useTranslations("Activity");

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        id="activity-help-sheet"
        side="right"
        className="flex w-[min(100vw,22rem)] max-w-[min(100vw,22rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-md"
      >
        <SheetHeader className="border-border shrink-0 border-b px-4 py-3 text-left">
          <SheetTitle>{t("sheetTitle")}</SheetTitle>
          <SheetDescription>{t("sheetDescription")}</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="space-y-4 px-4 py-4">
            <div className="text-muted-foreground text-sm leading-relaxed">{intro}</div>
            <Separator />
            <div>
              <h3 className="mb-2 font-medium text-muted-foreground text-xs uppercase tracking-wide">
                {t("recentActivity")}
              </h3>
              <TraceHistoryList />
            </div>
            {isChatRoute ? (
              <>
                <Separator />
                <div>
                  <h3 className="mb-2 font-medium text-muted-foreground text-xs uppercase tracking-wide">
                    {t("explainabilitySection")}
                  </h3>
                  <ExplainabilityPanel />
                </div>
              </>
            ) : null}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
