import createMiddleware from "next-intl/middleware";
import { type NextRequest, NextResponse } from "next/server";
import { AUTH_ACCESS_COOKIE_NAME } from "@/lib/auth-cookie";
import { routing } from "@/i18n/routing";

const handleI18n = createMiddleware(routing);

function stripLocale(pathname: string): string {
  const segments = pathname.split("/").filter(Boolean);
  if (segments.length === 0) return "/";
  const first = segments[0];
  if (routing.locales.includes(first as "en" | "es")) {
    const rest = segments.slice(1).join("/");
    return rest ? `/${rest}` : "/";
  }
  return pathname.startsWith("/") ? pathname : `/${pathname}`;
}

function localeFromPath(pathname: string): string {
  const first = pathname.split("/").find((s) => s.length > 0);
  if (
    first !== undefined &&
    routing.locales.includes(first as "en" | "es")
  ) {
    return first;
  }
  return routing.defaultLocale;
}

export default function proxy(request: NextRequest) {
  if (process.env.NEXT_PUBLIC_SKIP_AUTH === "true") {
    return handleI18n(request);
  }

  const pathname = request.nextUrl.pathname;
  const bare = stripLocale(pathname);
  const isAppRoute =
    bare.startsWith("/projects") ||
    bare.startsWith("/chat") ||
    bare.startsWith("/documents") ||
    bare.startsWith("/settings") ||
    bare.startsWith("/lab") ||
    bare.startsWith("/admin");

  if (isAppRoute) {
    const token = request.cookies.get(AUTH_ACCESS_COOKIE_NAME)?.value;
    if (!token) {
      const locale = localeFromPath(pathname);
      return NextResponse.redirect(new URL(`/${locale}/login`, request.url));
    }
  }

  return handleI18n(request);
}

export const config = {
  matcher: ["/", "/(en|es)/:path*"],
};
