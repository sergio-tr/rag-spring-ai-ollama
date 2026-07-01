import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Lab evaluation API contracts @api @chatAcceptance", () => {
  test("GET lab/status reports reference bundle readiness for evaluations", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/lab/status"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET lab/status") as {
      referenceBundleAvailable: boolean;
      referenceBundleValid: boolean;
      evaluations?: Record<string, unknown>;
    };
    expect(typeof body.referenceBundleAvailable).toBe("boolean");
    expect(typeof body.referenceBundleValid).toBe("boolean");
    expect(body.evaluations).toBeTruthy();
  });

  test("GET benchmarks/{kind}/runs/latest returns JSON 404 or latest run when data exists", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/lab/benchmarks/RAG_PRESET_END_TO_END/runs/latest"), {
      headers: { ...authHeaders(token), Accept: "application/json" },
    });
    const raw = await res.text();
    const body = parseJsonExpectNonHtml(raw, "GET latest benchmark run") as {
      success?: boolean;
      error?: { code?: string; message?: string };
      evaluationRunId?: string;
      status?: string;
    };
    if (res.status() === 404) {
      expect(body.success).toBe(false);
      expect(body.error?.code).toBeTruthy();
      return;
    }
    expect(res.status(), raw).toBe(200);
    expect(body.evaluationRunId ?? body.status).toBeTruthy();
  });

  test("POST benchmarks/LLM_JUDGE_QA/runs with unknown dataset returns JSON error", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.post(productUrl("/lab/benchmarks/LLM_JUDGE_QA/runs"), {
      headers: { ...authHeaders(token), "Content-Type": "application/json", Accept: "application/json" },
      data: {
        datasetId: "00000000-0000-4000-8000-000000000001",
        runKind: "PRODUCT_EXPLORATION",
        name: "api-e2e-invalid-dataset",
      },
    });
    const raw = await res.text();
    expect([400, 404]).toContain(res.status());
    const body = parseJsonExpectNonHtml(raw, "POST benchmark invalid dataset") as {
      success?: boolean;
      error?: { code?: string; message?: string };
    };
    expect(body.success).toBe(false);
    expect(body.error?.code).toBeTruthy();
  });

  test("GET lab/evaluation-models returns provider-aware catalog without Ollama tag dependency", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/lab/evaluation-models?capability=CHAT"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET lab/evaluation-models") as {
      effectiveProvider?: string;
      capability?: string;
      models?: Array<{ modelName: string; evalSelectable: boolean; blockedReason?: string | null }>;
    };
    expect(body.capability).toBe("CHAT");
    expect(["OPENAI_COMPATIBLE", "OLLAMA_NATIVE"]).toContain(body.effectiveProvider);
    expect(Array.isArray(body.models)).toBe(true);
    const serialized = JSON.stringify(body);
    if (body.effectiveProvider === "OPENAI_COMPATIBLE") {
      expect(serialized).not.toMatch(/11434|:11434/);
      for (const row of body.models ?? []) {
        if (row.blockedReason) {
          expect(row.blockedReason).not.toMatch(/ollama tag/i);
        }
      }
    }
  });
});
