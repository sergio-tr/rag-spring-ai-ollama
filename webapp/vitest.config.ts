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
        // Shell + dropdown menu: Radix-heavy; sidebar/panel/settings covered by focused tests and E2E.
        "src/components/layout/AppShell.tsx",
        "src/components/layout/ThemeLanguageMenu.tsx",
        // Large RAG settings form: manual/E2E validation; hooks remain gated.
        "src/features/settings/components/RagConfigForm.tsx",
        "src/features/settings/components/MeCanonicalJsonPanels.tsx",
      ],
      // Gate: align with backend JaCoCo bundle (≥80% lines); branches covered via api-client, SSE, and polling tests.
      thresholds: {
        lines: 80,
        statements: 80,
        functions: 80,
        branches: 80,
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
