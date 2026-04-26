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
        // Next.js App Router layouts are integration-heavy; pages are measured because we ship unit tests for critical pages.
        "src/app/**/layout.tsx",
        // Lab classifier train/eval/classify panels (extracted from page for Sonar); E2E/manual validation.
        "src/app/**/lab-classifier-panels.tsx",
        // Shared lab LLM/RAG evaluation runner (logic lifted from App Router pages); E2E + manual.
        "src/features/lab/components/lab-evaluation-run-card.tsx",
        // Shared Lab/Settings tab shell; thin layout glue covered by E2E navigation.
        "src/components/layout/app-subnav-section-layout.tsx",
        // shadcn/Radix UI primitives (thin presentation); behavior is covered indirectly via feature tests.
        "src/components/ui/**",
        // Shell + dropdown menu: Radix-heavy; sidebar/panel/settings covered by focused tests and E2E.
        "src/components/layout/AppShell.tsx",
        "src/components/layout/ThemeLanguageMenu.tsx",
        // Sidebar is measured: we keep focused tests for navigation and persistence behavior.
        // Move dialog is mostly UI glue; behavior is validated by chat flows + E2E.
        "src/features/chat/components/MoveConversationDialog.tsx",
        // RAG settings form is measured: keep smoke-level unit tests for gating behavior.
        "src/features/settings/components/MeCanonicalJsonPanels.tsx",
        // Lab registry section is UI-heavy; validated via E2E/manual flows.
        "src/features/lab/components/classifier-registry-section.tsx",
        // Auth view wrappers are measured: keep small unit tests for token parsing and error/success states.
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
