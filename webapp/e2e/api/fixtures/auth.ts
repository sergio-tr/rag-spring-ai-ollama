import { expect, type APIRequestContext } from "@playwright/test";
import { productUrl } from "./env";

export function authHeaders(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    Accept: "application/json",
  };
}

export type LoginTokens = {
  accessToken: string;
  refreshToken?: string;
};

export async function loginAndGetTokens(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<LoginTokens> {
  const res = await request.post(productUrl("/auth/login"), {
    data: { email, password },
    headers: { "Content-Type": "application/json" },
  });
  expect(res.ok(), `login failed: ${res.status()} ${await res.text()}`).toBeTruthy();
  const body = (await res.json()) as LoginTokens;
  expect(body.accessToken, "accessToken in login JSON").toBeTruthy();
  return body;
}

/**
 * POST {product}/auth/login — returns JWT access token or throws if login fails.
 */
export async function loginAndGetToken(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const body = await loginAndGetTokens(request, email, password);
  return body.accessToken;
}
