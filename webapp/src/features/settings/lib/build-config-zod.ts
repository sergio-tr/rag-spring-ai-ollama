import { z } from "zod";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";

function preprocessNumericInput(v: unknown): unknown {
  if (v === null || v === undefined) return undefined;
  if (typeof v === "string") {
    const trimmed = v.trim();
    if (trimmed === "") return undefined;
    return trimmed;
  }
  if (typeof v === "number" && Number.isNaN(v)) return undefined;
  return v;
}

function toFiniteNumber(v: unknown): number | undefined {
  if (typeof v === "number") return Number.isFinite(v) ? v : undefined;
  if (typeof v === "string") {
    const trimmed = v.trim();
    if (trimmed === "") return undefined;
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function normalizeFieldType(fieldType: unknown): "integer" | "number" | "boolean" | "string" | "text" {
  if (typeof fieldType !== "string") return "string";
  const normalized = fieldType.trim().toLowerCase();
  switch (normalized) {
    case "integer":
    case "int":
      return "integer";
    case "number":
    case "float":
    case "double":
    case "decimal":
      return "number";
    case "boolean":
    case "bool":
      return "boolean";
    case "text":
      return "text";
    default:
      return "string";
  }
}

/** Builds a Zod object for editable RAG config keys described by GET /config/schema. */
export function buildConfigValuesSchema(fields: ConfigSchemaField[]) {
  const editable = fields.filter((f) => f.userEditable);
  const shape: Record<string, z.ZodTypeAny> = {};
  for (const f of editable) {
    switch (normalizeFieldType(f.type)) {
      case "integer": {
        const min = toFiniteNumber(f.min);
        const max = toFiniteNumber(f.max);
        let n = z.coerce.number().int();
        if (min !== undefined) n = n.min(min);
        if (max !== undefined) n = n.max(max);
        shape[f.key] = z.preprocess(preprocessNumericInput, z.union([n, z.undefined()])).optional();
        break;
      }
      case "number": {
        const min = toFiniteNumber(f.min);
        const max = toFiniteNumber(f.max);
        let n = z.coerce.number();
        if (min !== undefined) n = n.min(min);
        if (max !== undefined) n = n.max(max);
        shape[f.key] = z.preprocess(preprocessNumericInput, z.union([n, z.undefined()])).optional();
        break;
      }
      case "boolean":
        shape[f.key] = z.boolean().optional();
        break;
      case "text":
      case "string":
      default: {
        const maxLen = toFiniteNumber(f.max);
        let s = z.string();
        if (maxLen !== undefined) s = s.max(maxLen);
        shape[f.key] = z.union([s, z.literal("")]).optional().transform((v) => (v === "" ? undefined : v));
        break;
      }
    }
  }
  return z.object(shape);
}

export type ConfigFormValues = Record<string, string | number | boolean | undefined>;
