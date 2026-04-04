import {
  AUTH_ACCESS_COOKIE_NAME,
  AUTH_REFRESH_COOKIE_NAME,
} from "@/lib/auth-cookie";
import { createTraceparent } from "@/lib/traceparent";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "http://localhost:9000";

const cookieSecure =
  process.env.NODE_ENV === "production" &&
  process.env.E2E_ALLOW_INSECURE_COOKIES !== "true";

const cookieOpts = {
  httpOnly: true,
  sameSite: "lax" as const,
  path: "/",
  secure: cookieSecure,
};

/**
 * Proxies refresh to the Spring API using the httpOnly refresh cookie on the Next origin.
 */
export async function POST() {
  const jar = await cookies();
  const refresh = jar.get(AUTH_REFRESH_COOKIE_NAME)?.value;
  if (!refresh) {
    return NextResponse.json({ error: "no_refresh" }, { status: 401 });
  }

  const upstream = await fetch(`${API_BASE}/api/auth/refresh`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      traceparent: createTraceparent(),
    },
    body: JSON.stringify({ refreshToken: refresh }),
  });

  if (!upstream.ok) {
    return NextResponse.json(
      { error: "refresh_failed" },
      { status: upstream.status },
    );
  }

  const data = (await upstream.json()) as {
    accessToken?: string;
    refreshToken?: string;
  };

  if (!data.accessToken) {
    return NextResponse.json({ error: "invalid_upstream" }, { status: 502 });
  }

  const res = NextResponse.json({
    ok: true,
    accessToken: data.accessToken,
  });
  res.cookies.set(AUTH_ACCESS_COOKIE_NAME, data.accessToken, cookieOpts);
  if (data.refreshToken) {
    res.cookies.set(AUTH_REFRESH_COOKIE_NAME, data.refreshToken, cookieOpts);
  }
  return res;
}
