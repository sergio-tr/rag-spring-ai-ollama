"use client";

import { routing } from "@/i18n/routing";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef } from "react";

type Props = Readonly<{ params: { slug: string[] } }>;

export default function BareLabSubpathRedirectPage({ params }: Props) {
  const router = useRouter();
  const didRunRef = useRef(false);

  const slugPath = useMemo(() => {
    const slug = Array.isArray(params.slug) ? params.slug : [];
    return slug.length > 0 ? `/${slug.map(encodeURIComponent).join("/")}` : "";
  }, [params.slug]);

  useEffect(() => {
    if (didRunRef.current) return;
    didRunRef.current = true;

    const { pathname, search, hash } = window.location;
    const target = `/${routing.defaultLocale}/lab${slugPath}${search ?? ""}${hash ?? ""}`;

    if (pathname.startsWith(`/${routing.defaultLocale}/lab`)) return;
    if (!pathname.startsWith("/lab")) return;
    router.replace(target);
  }, [router, slugPath]);

  return <p className="text-muted-foreground p-6 text-sm">Redirecting…</p>;
}

