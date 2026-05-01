import {
  AUTH_ACCESS_COOKIE_NAME,
  AUTH_REFRESH_COOKIE_NAME,
} from "@/lib/auth-cookie";
import { NextResponse } from "next/server";

export async function POST() {
  const res = NextResponse.json({ ok: true });
  res.cookies.set(AUTH_ACCESS_COOKIE_NAME, "", { path: "/", maxAge: 0 });
  res.cookies.set(AUTH_REFRESH_COOKIE_NAME, "", { path: "/", maxAge: 0 });
  return res;
}
