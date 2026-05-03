import * as path from "node:path";

/** Directory containing committed fixture files (run Playwright from `webapp/` so `process.cwd()` resolves). */
export function fixtureFilesDir(): string {
  return path.join(process.cwd(), "e2e", "fixtures", "files");
}

/** Committed plain-text sample for uploads when a file path is preferred over an in-memory buffer. */
export function sampleTextFilePath(): string {
  return path.join(fixtureFilesDir(), "sample.txt");
}
