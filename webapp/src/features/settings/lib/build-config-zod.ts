import { z } from "zod";
import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";

function numOrUndef(v: unknown): unknown {
  if (v === "" || v === null || v === undefined) return undefined;
  if (typeof v === "number" && Number.isNaN(v)) return undefined;
  return v;
}

/** Builds a Zod object for editable RAG config keys described by GET /config/schema. */
export function buildConfigValuesSchema(fields: ConfigSchemaField[]) {
  const editable = fields.filter((f) => f.userEditable);
  const shape: Record<string, z.ZodTypeAny> = {};
  for (const f of editable) {
    switch (f.type) {
      case "integer": {
        let n = z.number().int();
        if (f.min != null) n = n.min(f.min);
        if (f.max != null) n = n.max(f.max);
        shape[f.key] = z.preprocess(numOrUndef, n.optional());
        break;
      }
      case "number": {
        let n = z.number();
        if (f.min != null) n = n.min(f.min);
        if (f.max != null) n = n.max(f.max);
        shape[f.key] = z.preprocess(numOrUndef, n.optional());
        break;
      }
      case "boolean":
        shape[f.key] = z.boolean().optional();
        break;
      case "string":
      default:
        shape[f.key] = z.string().optional();
        break;
    }
  }
  return z.object(shape);
}

export type ConfigFormValues = Record<string, string | number | boolean | undefined>;
