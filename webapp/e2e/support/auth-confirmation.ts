import { expect, type APIRequestContext } from "@playwright/test";
import { loginAndGetToken, authHeaders } from "../api/fixtures/auth";
import { productUrl } from "../api/fixtures/env";
import { adminEmail, adminPassword } from "../fixtures/users";

type MailOutboxEntry = {
  purpose?: string;
  recipient?: string;
  bodyText?: string;
};

/**
 * Reads the latest EMAIL_CONFIRMATION link for a recipient from the admin mail-outbox (dev/e2e only).
 */
export async function fetchConfirmTokenFromOutbox(
  request: APIRequestContext,
  recipientEmail: string,
): Promise<string> {
  const adminToken = await loginAndGetToken(request, adminEmail(), adminPassword());
  const res = await request.get(productUrl("/admin/mail-outbox?limit=20"), {
    headers: authHeaders(adminToken),
  });
  expect(res.ok(), await res.text()).toBeTruthy();
  const entries = (await res.json()) as MailOutboxEntry[];
  const normalized = recipientEmail.trim().toLowerCase();
  for (const entry of entries) {
    if (entry.purpose !== "EMAIL_CONFIRMATION") continue;
    if (entry.recipient?.trim().toLowerCase() !== normalized) continue;
    const body = entry.bodyText ?? "";
    const match = body.match(/confirm-email\?token=([^&\s"']+)/);
    if (match?.[1]) {
      return decodeURIComponent(match[1]);
    }
  }
  throw new Error(`No confirmation outbox entry for ${recipientEmail}`);
}

export function uniqueM2AuthEmail(): string {
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  return `m2-e2e-${stamp}@example.com`;
}
