"use client";

import { routing } from "@/i18n/routing";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";

export default function BareLabRedirectPage() {
  const router = useRouter();
  const didRunRef = useRef(false);

  useEffect(() => {
    if (didRunRef.current) return;
    didRunRef.current = true;

    const { pathname, search, hash } = window.location;
    const target = `/${routing.defaultLocale}/lab${search ?? ""}${hash ?? ""}`;

    if (pathname.startsWith(`/${routing.defaultLocale}/lab`)) return;
    if (!pathname.startsWith("/lab")) return;
    router.replace(target);
  }, [router]);

  return <p className="text-muted-foreground p-6 text-sm">Redirecting…</p>;
}

