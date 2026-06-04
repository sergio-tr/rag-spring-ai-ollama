import { describe, expect, it } from "vitest";
import { documentStatusLabel, isTerminalDocumentStatus } from "./document-status-label";

describe("documentStatusLabel", () => {
  it("maps READY, ERROR, and ingesting states", () => {
    expect(documentStatusLabel("READY")).toBe("Ready");
    expect(documentStatusLabel("ERROR")).toBe("Failed");
    expect(documentStatusLabel("INGESTING")).toBe("Processing");
  });

  it("detects terminal statuses", () => {
    expect(isTerminalDocumentStatus("READY")).toBe(true);
    expect(isTerminalDocumentStatus("ERROR")).toBe(true);
    expect(isTerminalDocumentStatus("INGESTING")).toBe(false);
  });
});
