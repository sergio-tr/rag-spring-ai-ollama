import { defineConfig } from "vite";
import path from "node:path";
import react from "@vitejs/plugin-react";

/**
 * Vitest extends Vite `UserConfig` with `test`; this file is excluded from `tsconfig.json` because the published `vitest/config` types stub is circular and breaks `tsc`.
 *
 * Coverage gate: all application source under `src/**` (see `coverage.include`), excluding test files and type-only modules.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "json-summary", "lcov", "html"],
      include: ["src/**/*.{ts,tsx}"],
      exclude: [
        "**/*.test.{ts,tsx}",
        // Type-only barrel (no runtime statements to instrument meaningfully).
        "src/types/api.ts",
        // Test helpers are not product code.
        "src/test-utils/**",
        // Next.js App Router pages/layouts: large, async, and integration-heavy; covered by Playwright E2E. API route handlers under `src/app/api/**` remain in the gate.
        "src/app/**/page.tsx",
        "src/app/**/layout.tsx",
        // Lab classifier train/eval/classify panels (extracted from page for Sonar); E2E/manual validation.
        "src/app/**/lab-classifier-panels.tsx",
        // shadcn/Radix UI primitives (thin presentation); behavior is covered indirectly via feature tests.
        "src/components/ui/**",
        // App chrome (sidebar, shell, theme/i18n wiring): covered by E2E; thin composition only.
        "src/components/layout/**",
        "src/components/providers/**",
        "src/components/settings/**",
        // Large RAG settings form and lab classifier UI: manual/E2E validation; hooks remain gated.
        "src/features/settings/components/RagConfigForm.tsx",
        "src/features/settings/components/MeCanonicalJsonPanels.tsx",
        "src/features/lab/components/classifier-registry-section.tsx",
      ],
      // Gate: lines/statements/functions at 80% (backend/classifier parity). Branch aggregate stays
      // lower due to error-path branching in src/lib (api-client, async-task) and excluded E2E-heavy UI.
      thresholds: {
        lines: 80,
        statements: 80,
        functions: 80,
        branches: 74,
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
