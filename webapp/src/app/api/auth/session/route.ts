import {
  AUTH_ACCESS_COOKIE_NAME,
  AUTH_REFRESH_COOKIE_NAME,
} from "@/lib/auth-cookie";
import { NextResponse } from "next/server";

// Allow http:// smoke tests against `next start` (production NODE_ENV) when cookies must not be Secure.
const cookieSecure =
  process.env.NODE_ENV === "production" &&
  process.env.E2E_ALLOW_INSECURE_COOKIES !== "true";

const cookieOpts = {
  httpOnly: true,
  sameSite: "lax" as const,
  path: "/",
  secure: cookieSecure,
};

export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid_json" }, { status: 400 });
  }

  if (
    !body ||
    typeof body !== "object" ||
    !("accessToken" in body) ||
    typeof (body as { accessToken: unknown }).accessToken !== "string"
  ) {
    return NextResponse.json({ error: "invalid_body" }, { status: 400 });
  }

  const { accessToken, refreshToken } = body as {
    accessToken: string;
    refreshToken?: string;
  };

  const res = NextResponse.json({ ok: true });
  res.cookies.set(AUTH_ACCESS_COOKIE_NAME, accessToken, cookieOpts);
  if (refreshToken && typeof refreshToken === "string") {
    res.cookies.set(AUTH_REFRESH_COOKIE_NAME, refreshToken, cookieOpts);
  }
  return res;
}
