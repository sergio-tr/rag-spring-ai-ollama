import { describe, expect, it } from "vitest";
import { structuredConfigFields, partitionConfigFields } from "./rag-config-structured-fields";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";

describe("structuredConfigFields", () => {
  it("orders known keys and includes embeddingModel even when schema marks it non-editable", () => {
    const fields: ConfigSchemaField[] = [
      { key: "metadataEnabled", type: "boolean", userEditable: true },
      { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
      { key: "llmModel", type: "string", userEditable: true },
      { key: "embeddingModel", type: "string", userEditable: false },
    ];
    const ordered = structuredConfigFields(fields).map((field) => field.key);
    expect(ordered.indexOf("llmModel")).toBeLessThan(ordered.indexOf("topK"));
    expect(ordered).toContain("embeddingModel");
  });

  it("places llmSystemPrompt before model keys", () => {
    const fields: ConfigSchemaField[] = [
      { key: "topK", type: "integer", userEditable: true },
      { key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 },
      { key: "llmModel", type: "string", userEditable: true },
    ];
    const ordered = structuredConfigFields(fields).map((field) => field.key);
    expect(ordered.indexOf("llmSystemPrompt")).toBeLessThan(ordered.indexOf("llmModel"));
  });

  it("partitions instruction fields from behavior fields", () => {
    const fields: ConfigSchemaField[] = [
      { key: "llmSystemPrompt", type: "text", userEditable: true },
      { key: "topK", type: "integer", userEditable: true },
    ];
    const { instructionFields, behaviorFields } = partitionConfigFields(structuredConfigFields(fields));
    expect(instructionFields.map((f) => f.key)).toEqual(["llmSystemPrompt"]);
    expect(behaviorFields.map((f) => f.key)).toEqual(["topK"]);
  });

  it("appends unknown editable fields after ordered keys", () => {
    const fields: ConfigSchemaField[] = [
      { key: "llmModel", type: "string", userEditable: true },
      { key: "customFlag", type: "boolean", userEditable: true },
    ];
    const ordered = structuredConfigFields(fields).map((field) => field.key);
    expect(ordered).toEqual(["llmModel", "customFlag"]);
  });

  it("skips non-editable fields unless they are forced into the structured form", () => {
    const fields: ConfigSchemaField[] = [
      { key: "hiddenFlag", type: "boolean", userEditable: false },
      { key: "embeddingModel", type: "string", userEditable: false },
    ];
    const ordered = structuredConfigFields(fields).map((field) => field.key);
    expect(ordered).toEqual(["embeddingModel"]);
  });
});
