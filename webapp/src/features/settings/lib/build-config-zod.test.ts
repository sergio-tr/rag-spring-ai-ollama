import { describe, it, expect } from "vitest";
import { buildConfigValuesSchema } from "./build-config-zod";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";

describe("buildConfigValuesSchema", () => {
  const fields: ConfigSchemaField[] = [
    { key: "n", type: "integer", min: 0, max: 10, userEditable: true },
    { key: "f", type: "number", userEditable: true },
    { key: "b", type: "boolean", userEditable: true },
    { key: "s", type: "string", userEditable: true },
    { key: "hidden", type: "string", userEditable: false },
  ];

  it("only includes userEditable fields", () => {
    const schema = buildConfigValuesSchema(fields);
    const keys = Object.keys(schema.shape);
    expect(keys.sort()).toEqual(["b", "f", "n", "s"]);
  });

  it("parses integer with preprocess empty as undefined", () => {
    const schema = buildConfigValuesSchema(fields);
    const r = schema.safeParse({ n: "", s: "x" });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.n).toBeUndefined();
  });

  it("applies min/max for number and integer fields", () => {
    const narrow: ConfigSchemaField[] = [
      { key: "i", type: "integer", min: 1, max: 3, userEditable: true },
      { key: "f", type: "number", min: 0.5, max: 1.5, userEditable: true },
    ];
    const schema = buildConfigValuesSchema(narrow);
    expect(schema.safeParse({ i: 0 }).success).toBe(false);
    expect(schema.safeParse({ i: 4 }).success).toBe(false);
    expect(schema.safeParse({ i: 2 }).success).toBe(true);
    expect(schema.safeParse({ i: "2" }).success).toBe(true);
    expect(schema.safeParse({ i: 2.5 }).success).toBe(false);
    expect(schema.safeParse({ f: 0.4 }).success).toBe(false);
    expect(schema.safeParse({ f: 1 }).success).toBe(true);
    expect(schema.safeParse({ f: "1.2" }).success).toBe(true);
    expect(schema.safeParse({ f: 2 }).success).toBe(false);
  });
});
