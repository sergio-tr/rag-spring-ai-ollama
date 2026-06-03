import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";
import { sampleTextFilePath } from "../fixtures/documents";
import { uniqueProjectName } from "../fixtures/projects";
import {
  activeProjectIdFromUrl,
  authHeadersFromPage,
  createAndActivateProject,
  loginAsSeedUser,
} from "../support/helpers";
import { productApiUrl } from "../support/helpers";
import type { ProjectDocumentDto } from "../../src/types/api";

const BULK_COUNT = 5;

function bulkTextFixtures(): { name: string; mimeType: string; buffer: Buffer }[] {
  const base = readFileSync(sampleTextFilePath());
  return Array.from({ length: BULK_COUNT }, (_, i) => ({
    name: `bulk-doc-${i + 1}.txt`,
    mimeType: "text/plain",
    buffer: Buffer.concat([base, Buffer.from(`\n# bulk-${i + 1}\n`)]),
  }));
}

async function fetchProjectDocuments(
  page: import("@playwright/test").Page,
): Promise<ProjectDocumentDto[]> {
  const projectId = activeProjectIdFromUrl(page);
  const headers = await authHeadersFromPage(page);
  const res = await page.request.get(productApiUrl(`/projects/${projectId}/documents`), { headers });
  expect(res.ok()).toBeTruthy();
  return (await res.json()) as ProjectDocumentDto[];
}

test.describe("Closure project documents bulk ingestion @closure @fullstack @wave2", () => {
  test("uploads five files, all reach terminal without refresh @closure", async ({ page }) => {
    test.setTimeout(300_000);

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("bulk-ingest"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/, { timeout: 15_000 });
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });

    const fixtures = bulkTextFixtures();
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(fixtures);

    const names = fixtures.map((f) => f.name);

    await expect
      .poll(
        async () => {
          const docs = await fetchProjectDocuments(page);
          return names.every((n) => docs.some((d) => d.fileName === n));
        },
        { timeout: 60_000, intervals: [500, 1000, 2000] },
      )
      .toBe(true);

    await expect
      .poll(
        async () => {
          const docs = await fetchProjectDocuments(page);
          const uploaded = docs.filter((d) => names.includes(d.fileName));
          if (uploaded.length < BULK_COUNT) return "waiting";
          const ingesting = uploaded.filter((d) => d.status === "INGESTING").length;
          const terminal = uploaded.filter((d) => d.status === "READY" || d.status === "ERROR").length;
          return `${terminal}/${BULK_COUNT}:ingesting=${ingesting}`;
        },
        { timeout: 240_000, intervals: [1000, 2000, 3000, 5000] },
      )
      .toMatch(new RegExp(`^${BULK_COUNT}/${BULK_COUNT}:ingesting=0$`));

    const finalDocs = (await fetchProjectDocuments(page)).filter((d) => names.includes(d.fileName));
    expect(finalDocs).toHaveLength(BULK_COUNT);

    const ready = finalDocs.filter((d) => d.status === "READY");
    const failed = finalDocs.filter((d) => d.status === "ERROR");
    expect(ready.length + failed.length).toBe(BULK_COUNT);
    expect(finalDocs.some((d) => d.status === "INGESTING")).toBe(false);

    for (const row of finalDocs) {
      const badge = page.locator("tbody tr", { hasText: row.fileName }).getByTestId("document-status-badge");
      await expect(badge).toBeVisible();
      await expect(badge).toHaveAttribute("data-ingestion-state", row.status);
      if (row.status === "ERROR") {
        expect(row.errorMessage ?? "").not.toBe("");
      }
    }

    const statusesPath = process.env.E2E_INGESTION_STATUSES_JSON;
    if (statusesPath) {
      const { writeFileSync, mkdirSync } = await import("node:fs");
      const { dirname } = await import("node:path");
      mkdirSync(dirname(statusesPath), { recursive: true });
      writeFileSync(statusesPath, JSON.stringify(finalDocs, null, 2));
    }
  });
});
