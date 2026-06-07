import * as fs from "node:fs";
import * as path from "node:path";
import { expect, test, type Request, type Response } from "@playwright/test";
import { gotoLabEvaluationPage, prepareLabE2eTest } from "../support/lab-helpers";

const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../../.cursor/evidence/p0-rag-kb-upload-not-found/trace",
);
const DRAFT_KEY = "lab:evaluation-draft:v1:RAG_PRESET_END_TO_END";
/** Deliberately non-existent corpus id to reproduce stale localStorage draft. */
const STALE_CORPUS_ID = "00000000-0000-4000-8000-000000000001";

type CapturedRequest = {
  method: string;
  url: string;
  status: number;
  requestBodyPreview: string | null;
  responseBodyPreview: string;
  timestamp: string;
};

const INTERCEPT_RE =
  /knowledge|corpus|document|upload|\/lab\/|\/api\/v5\/lab|evaluation-corpus/i;

function previewBody(body: string | null, max = 2000): string | null {
  if (body == null) return null;
  return body.length > max ? `${body.slice(0, max)}…` : body;
}

function isRelevantUrl(url: string): boolean {
  return INTERCEPT_RE.test(url);
}

function minimalPdfBuffer(): Buffer {
  const pdf = `%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj
xref
0 4
0000000000 65535 f 
0000000010 00000 n 
0000000053 00000 n 
0000000102 00000 n 
trailer<</Size 4/Root 1 0 R>>
startxref
149
%%EOF`;
  return Buffer.from(pdf, "utf8");
}

async function readResponseBody(response: Response): Promise<string> {
  try {
    return await response.text();
  } catch {
    return "<unreadable>";
  }
}

async function readRequestBody(request: Request): Promise<string | null> {
  try {
    const postData = request.postData();
    if (postData == null) return null;
    if (postData.includes("Content-Disposition: form-data")) {
      const names = [...postData.matchAll(/filename="([^"]+)"/g)].map((m) => m[1]);
      return `[multipart/form-data files=${names.join(", ") || "?"}]`;
    }
    return postData;
  } catch {
    return null;
  }
}

test.describe("DEBUG RAG KB upload Not found @debug", () => {
  test("capture failing network on stale corpusId upload", async ({ page }, testInfo) => {
    test.setTimeout(120_000);
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });

    const logLines: string[] = [];
    const captured: CapturedRequest[] = [];

    const log = (line: string) => {
      logLines.push(line);
      console.log(line);
    };

    await prepareLabE2eTest(page);

    await page.addInitScript(
      ({ key, corpusId }) => {
        try {
          const raw = localStorage.getItem(key);
          const base = raw ? (JSON.parse(raw) as Record<string, unknown>) : { v: 1 };
          base.corpusId = corpusId;
          localStorage.setItem(key, JSON.stringify(base));
        } catch {
          localStorage.setItem(key, JSON.stringify({ v: 1, corpusId }));
        }
      },
      { key: DRAFT_KEY, corpusId: STALE_CORPUS_ID },
    );

    page.on("request", (request) => {
      if (!isRelevantUrl(request.url())) return;
      log(`→ REQ ${request.method()} ${request.url()}`);
    });

    page.on("response", async (response) => {
      const request = response.request();
      if (!isRelevantUrl(request.url())) return;
      const entry: CapturedRequest = {
        method: request.method(),
        url: request.url(),
        status: response.status(),
        requestBodyPreview: previewBody(await readRequestBody(request)),
        responseBodyPreview: previewBody(await readResponseBody(response), 4000) ?? "",
        timestamp: new Date().toISOString(),
      };
      captured.push(entry);
      log(`← RES ${entry.status} ${entry.method} ${entry.url}`);
      if (entry.responseBodyPreview) {
        log(`   body: ${entry.responseBodyPreview.slice(0, 300)}`);
      }
    });

    log("=== Navigate LAB → RAG Evaluation ===");
    await gotoLabEvaluationPage(page, "rag");

    const kbPanel = page.getByTestId("lab-evaluation-corpus-panel");
    await expect(kbPanel).toBeVisible({ timeout: 15_000 });

    await expect
      .poll(async () => (await page.getByTestId("lab-kb-error").textContent()) ?? "", {
        timeout: 15_000,
        intervals: [500, 1000, 2000],
      })
      .toMatch(/not found/i);

    await page.screenshot({ path: path.join(EVIDENCE_DIR, "screenshot-not-found.png"), fullPage: true });

    log("=== Attempt PDF upload ===");
    const uploadInput = page.getByTestId("lab-corpus-upload-input");
    await uploadInput.setInputFiles({
      name: "debug-kb-sample.pdf",
      mimeType: "application/pdf",
      buffer: minimalPdfBuffer(),
    });

    await page.waitForTimeout(5_000);

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "screenshot-after-upload-attempt.png"),
      fullPage: true,
    });

    const failing =
      captured.find((r) => r.status === 404) ??
      captured.filter((r) => r.status >= 400).sort((a, b) => b.status - a.status)[0] ??
      null;

    fs.writeFileSync(path.join(EVIDENCE_DIR, "network-requests.json"), JSON.stringify(captured, null, 2));
    fs.writeFileSync(path.join(EVIDENCE_DIR, "failing-request.json"), JSON.stringify(failing, null, 2));
    fs.writeFileSync(path.join(EVIDENCE_DIR, "e2e-debug.log"), `${logLines.join("\n")}\n`);

    const tracePath = testInfo.outputPath("trace.zip");
    if (fs.existsSync(tracePath)) {
      fs.copyFileSync(tracePath, path.join(EVIDENCE_DIR, "trace.zip"));
    }

    log(`Captured ${captured.length} relevant requests; failing=${failing?.method ?? "none"} ${failing?.url ?? ""}`);

    expect(failing, "Expected at least one 4xx response for stale corpusId scenario").not.toBeNull();
    expect(failing!.status).toBe(404);
  });
});
