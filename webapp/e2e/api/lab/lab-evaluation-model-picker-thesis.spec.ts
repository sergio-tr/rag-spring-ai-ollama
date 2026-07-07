import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

const THESIS_CHAT_MODELS = [
  "deepseek-r1:1.5b",
  "llama3.2:3b",
  "qwen3.5:2b",
  "qwen3.5:4b",
  "gemma4:e2b",
  "gemma4:e4b",
  "deepseek-r1:7b",
  "gemma4:12b",
  "qwen3.5:9b",
  "deepseek-v2:16b",
  "gpt-oss:20b",
  "gemma4:26b",
  "qwen3.6:27b",
];

const THESIS_EMBEDDING_MODELS = [
  "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
  "mxbai-embed-large",
  "bge-m3",
  "snowflake-arctic-embed2",
];

test.describe("Lab evaluation model picker thesis matrix @api", () => {
  test("thesis chat models appear on lab evaluation-models when OPENAI_COMPATIBLE", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/lab/evaluation-models?capability=CHAT"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET lab/evaluation-models CHAT") as {
      effectiveProvider?: string;
      models?: Array<{ modelName: string; evalSelectable: boolean }>;
    };
    test.skip(body.effectiveProvider !== "OPENAI_COMPATIBLE", "thesis matrix requires OPENAI_COMPATIBLE");
    const names = new Set((body.models ?? []).map((m) => m.modelName));
    for (const id of THESIS_CHAT_MODELS) {
      expect(names, `missing thesis chat model ${id}`).toContain(id);
    }
    const gemmaE2b = (body.models ?? []).find((m) => m.modelName === "gemma4:e2b");
    expect(gemmaE2b, "gemma4:e2b in catalog").toBeTruthy();
    expect(gemmaE2b?.evalSelectable, "gemma4:e2b evalSelectable").toBe(true);
  });

  test("thesis embedding models appear and bge-m3 is eval selectable", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/lab/evaluation-models?capability=EMBEDDING"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET lab/evaluation-models EMBEDDING") as {
      effectiveProvider?: string;
      models?: Array<{
        modelName: string;
        evalSelectable: boolean;
        compatibleWithCurrentVectorStore?: boolean;
      }>;
    };
    test.skip(body.effectiveProvider !== "OPENAI_COMPATIBLE", "thesis matrix requires OPENAI_COMPATIBLE");
    const byName = new Map((body.models ?? []).map((m) => [m.modelName, m]));
    for (const id of THESIS_EMBEDDING_MODELS) {
      expect(byName.has(id), `missing thesis embedding model ${id}`).toBe(true);
      const row = byName.get(id)!;
      expect(row.compatibleWithCurrentVectorStore).toBe(true);
      expect(row.evalSelectable, `${id} should be eval selectable`).toBe(true);
    }
  });
});
