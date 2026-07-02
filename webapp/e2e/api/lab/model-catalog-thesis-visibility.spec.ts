import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Model catalog thesis visibility @api", () => {
  test("llm catalog marks bge-m3 governance allowed after thesis allowlist migration", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/llm/catalog"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET llm/catalog") as {
      models?: Array<{
        provider: string;
        modelName: string;
        capability: string;
        governanceAllowed?: boolean;
      }>;
    };
    const bge = (body.models ?? []).find(
      (m) => m.provider === "OPENAI_COMPATIBLE" && m.modelName === "bge-m3" && m.capability === "EMBEDDING",
    );
    expect(bge, "bge-m3 embedding row").toBeTruthy();
    expect(bge?.governanceAllowed).toBe(true);
  });

  test("deepseek-r1:1.5b is in configured catalog and gemma4:e2b is not", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/llm/catalog"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET llm/catalog") as {
      models?: Array<{ provider: string; modelName: string; capability: string; available?: boolean }>;
    };
    const chat = (body.models ?? []).filter((m) => m.provider === "OPENAI_COMPATIBLE" && m.capability === "CHAT");
    const names = new Set(chat.map((m) => m.modelName));
    expect(names).toContain("deepseek-r1:1.5b");
    expect(names).not.toContain("gemma4:e2b");
  });
});
