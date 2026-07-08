import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { skipIfBlockedEnvironment } from "../fixtures/blocked-environment";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";
import { runApiStackPreflight } from "../fixtures/stack-preflight";

type MeSelectableLlmModelDto = {
  modelName: string;
  displayName: string;
  selectable: boolean;
  disabledReason?: string | null;
  isDefault?: boolean;
  runtimeStatus?: string;
};

type MeSelectableLlmModelsResponseDto = {
  provider: string;
  capability: string;
  models: MeSelectableLlmModelDto[];
};

test.describe("P0 BL-005 provider-aware chat model selector @api @p0", () => {
  test.beforeAll(async ({ request }) => {
    try {
      await runApiStackPreflight(request);
    } catch (e) {
      skipIfBlockedEnvironment(e);
    }
  });

  test("GET /me/llm/selectable-models returns non-empty CHAT catalog without /models?type=LLM", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);

    const res = await request.get(
      productUrl("/me/llm/selectable-models?capability=CHAT"),
      { headers: authHeaders(token) },
    );
    const body = parseJsonExpectNonHtml(
      await res.text(),
      "GET /me/llm/selectable-models",
    ) as MeSelectableLlmModelsResponseDto;
    expect(res.status()).toBe(200);
    expect(body.capability).toBe("CHAT");
    expect(body.models.length).toBeGreaterThan(0);
    expect(body.models.some((m) => m.selectable && m.modelName.trim().length > 0)).toBe(true);

    const legacy = await request.get(productUrl("/models?type=LLM"), {
      headers: authHeaders(token),
    });
    expect([200, 404, 501]).toContain(legacy.status());
  });
});
