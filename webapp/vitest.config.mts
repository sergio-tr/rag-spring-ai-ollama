import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path, { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

/**
 * Vitest config as ESM to avoid CJS `require()` of ESM-only deps.
 *
 * Coverage gate: all application source under `src/**` (see `coverage.include`),
 * excluding test files and type-only modules.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "happy-dom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: true,
    coverage: {
      provider: "istanbul",
      reporter: ["text", "json-summary", "lcov", "html"],
      include: ["src/**"],
      exclude: [
        "**/*.test.{ts,tsx}",
        // Type-only barrel (no runtime statements to instrument meaningfully).
        "src/types/api.ts",
        // Test helpers are not product code.
        "src/test-utils/**",
        // Next.js App Router layouts/pages are integration-heavy; only measure the few we unit test.
        "src/app/**/page.tsx",
        "!src/app/[locale]/(app)/chat/page.tsx",
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
        // Sidebar is measured (has focused unit tests).
        // Move dialog is mostly UI glue; behavior is validated by chat flows + E2E.
        "src/features/chat/components/MoveConversationDialog.tsx",
        // RAG settings form is measured (has smoke-level unit tests).
        "src/features/settings/components/MeCanonicalJsonPanels.tsx",
        // Lab registry section is UI-heavy; validated via E2E/manual flows.
        "src/features/lab/components/classifier-registry-section.tsx",
        // Auth view wrappers are measured (has small unit tests).
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
      "@": path.resolve(here, "./src"),
    },
  },
});

