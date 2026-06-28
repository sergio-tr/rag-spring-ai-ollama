import * as path from "node:path";

/** Directory containing committed fixture files (run Playwright from `webapp/` so `process.cwd()` resolves). */
export function fixtureFilesDir(): string {
  return path.join(process.cwd(), "e2e", "fixtures", "files");
}

/** Committed plain-text sample for uploads when a file path is preferred over an in-memory buffer. */
export function sampleTextFilePath(): string {
  return path.join(fixtureFilesDir(), "sample.txt");
}

/** ACTA fixture for LAB knowledge-base manager closure tests. */
export function actaKnowledgeBaseFilePath(): string {
  return path.join(fixtureFilesDir(), "acta-24-02-2025.txt");
}

/** Optional bootstrap acta shipped with rag-service test resources (reference workbook grounding). */
export function ragClasspathBootstrapActaFilePath(): string {
  return path.join(process.cwd(), "..", "rag-service", "src", "test", "resources", "docs", "bootstrap-acta.txt");
}
