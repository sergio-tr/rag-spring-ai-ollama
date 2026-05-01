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

  it("parses integer and number inputs consistently", () => {
    const schema = buildConfigValuesSchema(fields);
    const emptyInt = schema.safeParse({ n: "", s: "x" });
    expect(emptyInt.success).toBe(true);
    if (emptyInt.success) expect(emptyInt.data.n).toBeUndefined();

    const intFromString = schema.safeParse({ n: "2" });
    expect(intFromString.success).toBe(true);
    if (intFromString.success) expect(intFromString.data.n).toBe(2);

    const numberFromString = schema.safeParse({ f: "1.25" });
    expect(numberFromString.success).toBe(true);
    if (numberFromString.success) expect(numberFromString.data.f).toBe(1.25);

    const intFromNumber = schema.safeParse({ n: 2 });
    expect(intFromNumber.success).toBe(true);
    if (intFromNumber.success) expect(intFromNumber.data.n).toBe(2);

    const numberFromNumber = schema.safeParse({ f: 1.25 });
    expect(numberFromNumber.success).toBe(true);
    if (numberFromNumber.success) expect(numberFromNumber.data.f).toBe(1.25);

    const intFromWhitespaceString = schema.safeParse({ n: " 2 " });
    expect(intFromWhitespaceString.success).toBe(true);
    if (intFromWhitespaceString.success) expect(intFromWhitespaceString.data.n).toBe(2);

    const numberFromWhitespaceString = schema.safeParse({ f: " 1.25 " });
    expect(numberFromWhitespaceString.success).toBe(true);
    if (numberFromWhitespaceString.success) expect(numberFromWhitespaceString.data.f).toBe(1.25);

    expect(schema.safeParse({ n: "abc" }).success).toBe(false);
    expect(schema.safeParse({ f: "abc" }).success).toBe(false);
  });

  it("applies min/max for number and integer fields", () => {
    const narrow: ConfigSchemaField[] = [
      { key: "i", type: "integer", min: 1, max: 3, userEditable: true },
      { key: "f", type: "number", min: 0.5, max: 1.5, userEditable: true },
    ];
    const schema = buildConfigValuesSchema(narrow);
    const intMinFail = schema.safeParse({ i: 0 });
    expect(intMinFail.success).toBe(false);

    const intMaxFail = schema.safeParse({ i: 4 });
    expect(intMaxFail.success).toBe(false);

    const intPass = schema.safeParse({ i: 2 });
    expect(intPass.success).toBe(true);
    if (intPass.success) expect(intPass.data.i).toBe(2);

    const intStringPass = schema.safeParse({ i: "2" });
    expect(intStringPass.success).toBe(true);
    if (intStringPass.success) expect(intStringPass.data.i).toBe(2);

    const intDecimalFail = schema.safeParse({ i: 2.5 });
    expect(intDecimalFail.success).toBe(false);

    const numMinFail = schema.safeParse({ f: 0.4 });
    expect(numMinFail.success).toBe(false);

    const numPass = schema.safeParse({ f: 1 });
    expect(numPass.success).toBe(true);
    if (numPass.success) expect(numPass.data.f).toBe(1);

    const numStringPass = schema.safeParse({ f: "1.2" });
    expect(numStringPass.success).toBe(true);
    if (numStringPass.success) expect(numStringPass.data.f).toBe(1.2);

    const numMaxFail = schema.safeParse({ f: 2 });
    expect(numMaxFail.success).toBe(false);
  });

  it("handles string min/max bounds from API safely", () => {
    const fromApi = [
      { key: "i", type: "integer", min: "1", max: "3", userEditable: true },
      { key: "f", type: "number", min: "0.5", max: "1.5", userEditable: true },
    ] as unknown as ConfigSchemaField[];
    const schema = buildConfigValuesSchema(fromApi);
    expect(schema.safeParse({ i: 2 }).success).toBe(true);
    expect(schema.safeParse({ i: 0 }).success).toBe(false);
    expect(schema.safeParse({ n: "", s: "x" }).success).toBe(true);
    expect(schema.safeParse({ f: 1.2 }).success).toBe(true);
    expect(schema.safeParse({ f: 2 }).success).toBe(false);
  });

  it("ignores malformed string bounds instead of rejecting all values", () => {
    const malformed = [
      { key: "i", type: "integer", min: "x", max: "3", userEditable: true },
      { key: "f", type: "number", min: "0.5", max: "bad", userEditable: true },
    ] as unknown as ConfigSchemaField[];
    const schema = buildConfigValuesSchema(malformed);

    expect(schema.safeParse({ i: 2 }).success).toBe(true);
    expect(schema.safeParse({ i: 4 }).success).toBe(false);
    expect(schema.safeParse({ f: 0.2 }).success).toBe(false);
    expect(schema.safeParse({ f: 10 }).success).toBe(true);
  });

  it("normalizes field type aliases and casing from API", () => {
    const fromApi = [
      { key: "i", type: " INT ", min: "1", max: "3", userEditable: true },
      { key: "f", type: "FLOAT", min: "0.5", max: "1.5", userEditable: true },
      { key: "b", type: "Bool", userEditable: true },
    ] as unknown as ConfigSchemaField[];
    const schema = buildConfigValuesSchema(fromApi);

    expect(schema.safeParse({ i: 2 }).success).toBe(true);
    expect(schema.safeParse({ i: 4 }).success).toBe(false);
    expect(schema.safeParse({ f: 1.2 }).success).toBe(true);
    expect(schema.safeParse({ f: 2 }).success).toBe(false);
    expect(schema.safeParse({ b: true }).success).toBe(true);
    expect(schema.safeParse({ b: "true" }).success).toBe(false);
  });

  it("normalizes type whitespace and defaults unknown aliases to string", () => {
    const fromApi = [
      { key: "intLike", type: "  iNt  ", userEditable: true },
      { key: "numLike", type: " \tDeCiMaL\n", userEditable: true },
      { key: "fallback", type: "customType", userEditable: true },
    ] as unknown as ConfigSchemaField[];
    const schema = buildConfigValuesSchema(fromApi);

    expect(schema.safeParse({ intLike: "3" }).success).toBe(true);
    expect(schema.safeParse({ intLike: "3.5" }).success).toBe(false);
    expect(schema.safeParse({ numLike: "3.5" }).success).toBe(true);
    expect(schema.safeParse({ fallback: "abc" }).success).toBe(true);
    expect(schema.safeParse({ fallback: 123 }).success).toBe(false);
  });
});
