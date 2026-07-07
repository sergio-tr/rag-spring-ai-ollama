import { describe, expect, it } from "vitest";
import { structuredConfigFieldsForMode } from "./rag-config-structured-fields";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";

describe("structuredConfigFieldsForMode phase 2.1", () => {
  const fields: ConfigSchemaField[] = [
    { key: "topK", type: "integer", userEditable: true },
    { key: "similarityThreshold", type: "number", userEditable: true },
    { key: "expansionEnabled", type: "boolean", userEditable: true },
    { key: "toolsEnabled", type: "boolean", userEditable: true },
    { key: "metadataEnabled", type: "boolean", userEditable: true },
    { key: "materializationStrategy", type: "string", userEditable: true },
    { key: "llmModel", type: "string", userEditable: true },
  ];

  it("includes retrieval parameters in user assistant configuration", () => {
    const userKeys = structuredConfigFieldsForMode(fields, "user").map((field) => field.key);
    expect(userKeys).toContain("topK");
    expect(userKeys).toContain("similarityThreshold");
    expect(userKeys).not.toContain("toolsEnabled");
    expect(userKeys).not.toContain("materializationStrategy");
  });

  it("includes only retrieval parameters and excludes feature toggles in project configuration", () => {
    const projectKeys = structuredConfigFieldsForMode(fields, "project").map((field) => field.key);
    expect(projectKeys).toContain("topK");
    expect(projectKeys).toContain("similarityThreshold");
    expect(projectKeys).not.toContain("expansionEnabled");
    expect(projectKeys).not.toContain("toolsEnabled");
    expect(projectKeys).not.toContain("metadataEnabled");
  });
});
