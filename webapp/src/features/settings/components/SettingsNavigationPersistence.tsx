"use client";

import type { ReactNode } from "react";
import { Suspense, useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { usePathname, useRouter } from "@/navigation";
import { persistSettingsPath } from "@/features/settings/lib/settings-last-path";
import { resolveSettingsPathFromTabQuery } from "@/features/settings/lib/settings-tab-url";

export function SettingsLastPathRecorder({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();

  useEffect(() => {
    persistSettingsPath(pathname ?? "");
  }, [pathname]);

  return <>{children}</>;
}

/**
 * When the user opens `/settings?tab=…`, replace with the canonical segmented route (or `/settings` if invalid/general).
 * Client-side only; no full reload. Invalid `tab` falls back to `/settings`.
 */
export function SettingsTabQueryNormalizerInner() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const router = useRouter();
  const tabParam = searchParams.get("tab");

  useEffect(() => {
    if (pathname !== "/settings") return;
    if (tabParam === null) return;
    const target = resolveSettingsPathFromTabQuery(tabParam);
    router.replace(target);
  }, [pathname, router, tabParam]);

  return null;
}

export function SettingsTabQueryNormalizer() {
  return (
    <Suspense fallback={null}>
      <SettingsTabQueryNormalizerInner />
    </Suspense>
  );
}
