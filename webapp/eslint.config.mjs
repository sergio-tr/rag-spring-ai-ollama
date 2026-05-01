import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Vitest/coverage output (local runs; absent on fresh CI checkout until tests run).
    "coverage/**",
    // Typedoc output committed for docs hosting; not hand-maintained TS/TSX.
    "docs/api/**",
  ]),
]);

export default eslintConfig;
