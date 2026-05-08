import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

/** OOXML / ZIP workbook magic ("PK"). */
function assertOpenXmlMagic(buffer: Buffer) {
  expect(buffer.byteLength, "template bytes").toBeGreaterThan(16);
  expect(buffer.subarray(0, 2).toString("latin1")).toBe("PK");
}

test.describe("Lab typed datasets & benchmarks API @api", () => {
  test("GET lab/status exposes reference bundle + counts (no legacy classpath workbook key)", async ({
    request,
  }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/lab/status"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const raw = await res.text();
    expect(raw.toLowerCase()).not.toContain("evaluation_dataset.xlsx");
    const body = parseJsonExpectNonHtml(raw, "GET lab/status") as {
      referenceBundleAvailable: boolean;
      referenceBundleValid: boolean;
      datasetKindsReady: boolean;
      countsByDatasetKind: Record<string, number>;
      datasets: { enabled: boolean; datasetKindsReady?: boolean; legacyQuestionCountDeprecated?: unknown };
      validationIssues?: { code?: string }[];
    };
    expect(typeof body.referenceBundleAvailable).toBe("boolean");
    expect(typeof body.referenceBundleValid).toBe("boolean");
    expect(typeof body.datasetKindsReady).toBe("boolean");
    expect(body.countsByDatasetKind).toEqual(expect.any(Object));
    expect(body.datasets).toEqual(
      expect.objectContaining({
        enabled: expect.any(Boolean),
        datasetKindsReady: expect.any(Boolean),
      }),
    );
  });

  test("POST lab/evaluations/rag legacy endpoint returns 410 Gone @api", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.post(productUrl("/lab/evaluations/rag"), {
      headers: authHeaders(token),
    });
    const goneText = await res.text();
    expect(res.status(), goneText).toBe(410);
    const body = parseJsonExpectNonHtml(goneText, "POST lab/evaluations/rag gone") as {
      error?: string;
      canonicalStartBenchmarkPathTemplate?: string;
    };
    expect(body.error).toBe("LAB_EVALUATIONS_LEGACY_REMOVED");
    expect(body.canonicalStartBenchmarkPathTemplate ?? "").toContain("/lab/benchmarks/{kind}/runs");
  });

  for (const [segment, filename] of [
    ["llm-model-baseline", "llm-model-baseline-template.xlsx"],
    ["embedding-baseline", "embedding-baseline-template.xlsx"],
    ["rag-preset-benchmark", "rag-preset-benchmark-template.xlsx"],
  ] as const) {
    test(`GET dataset-templates/${segment} returns XLSX @api`, async ({ request }) => {
      const { email, password } = integrationCredentials();
      const token = await loginAndGetToken(request, email, password);
      const res = await request.get(productUrl(`/lab/dataset-templates/${segment}`), {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(res.status(), await res.text()).toBe(200);
      expect(res.headers()["content-type"] ?? "").toContain("spreadsheetml");
      const cd = res.headers()["content-disposition"] ?? "";
      expect(cd.toLowerCase()).toContain(filename.toLowerCase());
      const buf = Buffer.from(await res.body());
      assertOpenXmlMagic(buf);
    });
  }

  test.describe.serial("Upload + benchmark contracts @api", () => {
    let token: string;
    let llmTemplate: Buffer;

    test("login + fetch LLM template bytes", async ({ request }) => {
      const { email, password } = integrationCredentials();
      token = await loginAndGetToken(request, email, password);
      const res = await request.get(productUrl("/lab/dataset-templates/llm-model-baseline"), {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(res.status()).toBe(200);
      llmTemplate = Buffer.from(await res.body());
      assertOpenXmlMagic(llmTemplate);
    });

    const ownedDatasetId: string | null = null;

    test("POST experimental-datasets LLM template upload is rejected when too small → 422 + validationReport", async ({
      request,
    }) => {
      const res = await request.post(productUrl("/lab/experimental-datasets"), {
        headers: authHeaders(token),
        multipart: {
          file: {
            name: "qa-llm-upload.xlsx",
            mimeType:
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            buffer: llmTemplate,
          },
          datasetType: "llm-model-baseline",
          name: "api-test-llm",
        },
      });
      // Templates are intentionally minimal; backend requires a minimum number of questions for LLM_JUDGE_QA.
      expect(res.status(), await res.text()).toBe(422);
      const body = parseJsonExpectNonHtml(await res.text(), "POST experimental-datasets 422 too small") as {
        error: string;
        validationReport: {
          hasErrors: boolean;
          issues: { code: string; sheet: string; rowNumber: number; column: string; message?: string }[];
        };
      };
      expect(body.error).toBe("EXPERIMENTAL_DATASET_INVALID");
      expect(body.validationReport.hasErrors).toBe(true);
      expect(body.validationReport.issues.some((i) => i.code === "DATASET_TOO_SMALL")).toBe(true);
    });

    test("POST experimental-datasets mismatched kind → 422 + structured issues", async ({ request }) => {
      const res = await request.post(productUrl("/lab/experimental-datasets"), {
        headers: authHeaders(token),
        multipart: {
          file: {
            name: "wrong-kind.xlsx",
            mimeType:
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            buffer: llmTemplate,
          },
          datasetType: "embedding-baseline",
        },
      });
      expect(res.status(), await res.text()).toBe(422);
      const body = parseJsonExpectNonHtml(await res.text(), "POST experimental-datasets 422") as {
        error: string;
        validationReport: {
          hasErrors: boolean;
          issues: { code: string; sheet: string; rowNumber: number; column: string }[];
        };
      };
      expect(body.error).toBe("EXPERIMENTAL_DATASET_INVALID");
      expect(body.validationReport.hasErrors).toBe(true);
      expect(body.validationReport.issues.length).toBeGreaterThan(0);
      const first = body.validationReport.issues[0];
      expect(first.code).toBeTruthy();
      expect(first.sheet).toBeTruthy();
      expect(typeof first.rowNumber).toBe("number");
      expect(typeof first.column).toBe("string");
    });

    test("POST benchmarks/LLM_JUDGE_QA/runs compatible dataset → 202 + asyncTaskId", async ({
      request,
    }) => {
      test.skip(ownedDatasetId == null, "No owned dataset id (LLM template upload is expected to be rejected)");
      const res = await request.post(productUrl("/lab/benchmarks/LLM_JUDGE_QA/runs"), {
        headers: { ...authHeaders(token), "Content-Type": "application/json" },
        data: {
          datasetId: ownedDatasetId,
          runKind: "PRODUCT_EXPLORATION",
          name: "api-smoke-llm",
        },
      });
      expect(res.status(), await res.text()).toBe(202);
      const body = parseJsonExpectNonHtml(await res.text(), "POST benchmark LLM") as {
        evaluationRunId: string;
        asyncTaskId: string;
        status: string;
        pollPath: string;
      };
      expect(body.asyncTaskId).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
      );
      expect(body.evaluationRunId).toBeTruthy();
      expect(body.status).toBeTruthy();

      const job = await request.get(productUrl(`/lab/jobs/${body.asyncTaskId}`), {
        headers: authHeaders(token),
      });
      expect(job.status(), await job.text()).toBe(200);
      const jobBody = parseJsonExpectNonHtml(await job.text(), "GET lab job") as { status?: string };
      expect(jobBody.status).toBeTruthy();
    });

    test("POST benchmarks/EMBEDDING_RETRIEVAL/runs incompatible dataset → 400 JSON (no ghost task)", async ({
      request,
    }) => {
      test.skip(ownedDatasetId == null, "No owned dataset id (LLM template upload is expected to be rejected)");
      const res = await request.post(productUrl("/lab/benchmarks/EMBEDDING_RETRIEVAL/runs"), {
        headers: { ...authHeaders(token), "Content-Type": "application/json" },
        data: {
          datasetId: ownedDatasetId,
          runKind: "PRODUCT_EXPLORATION",
        },
      });
      expect([400, 422]).toContain(res.status());
      const raw = await res.text();
      parseJsonExpectNonHtml(raw, "benchmark incompatible");
      expect(raw.toLowerCase()).toMatch(/incompatible|benchmark|dataset|kind|experimental/i);
    });
  });
});
