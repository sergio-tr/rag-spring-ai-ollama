import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path, { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { isSuppressedTestConsoleNoise } from "./src/test-utils/suppress-test-console-noise";

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
    /** Drop RTL act noise, RQ undefined-data warnings, and upload-zone poll stack traces from Vitest stderr. */
    onConsoleLog(log, type) {
      if (type === "stdout") return undefined;
      if (isSuppressedTestConsoleNoise(log)) return false;
      return undefined;
    },
    coverage: {
      // Istanbul is producing empty LCOV locally (0/0 statements).
      // V8 coverage is the supported default in Vitest v4 and generates stable LCOV for Sonar.
      provider: "v8",
      reporter: ["text", "json-summary", "lcov", "html"],
      // V8 coverage needs explicit file globs (directories alone may yield 0/0).
      include: ["src/**/*.{ts,tsx,js,jsx}"],
      exclude: [
        "**/*.test.{ts,tsx}",
        // Type-only barrel (no runtime statements to instrument meaningfully).
        "src/types/api.ts",
        // Test helpers are not product code.
        "src/test-utils/**",
        // Next.js App Router layouts/pages are integration-heavy; only measure the few we unit test.
        "src/app/**/page.tsx",
        "src/app/**/layout.tsx",
        // Lab classifier train/eval/classify panels (extracted from page for Sonar); E2E/manual validation.
        "src/app/**/lab-classifier-panels.tsx",
        // Shared lab LLM/RAG evaluation runner (logic lifted from App Router pages); E2E + manual.
        "src/features/lab/components/lab-evaluation-run-card.tsx",
        // Hyperparameters UI shell for the evaluation run card; payload logic in lab-generation-hyperparameters.
        "src/features/lab/components/lab-hyperparameters-form.tsx",
        // Shared Lab/Settings tab shell; thin layout glue covered by E2E navigation.
        "src/components/layout/app-subnav-section-layout.tsx",
        // shadcn/Radix UI primitives (thin presentation); behavior is covered indirectly via feature tests.
        "src/components/ui/**",
        // Shell + dropdown menu: Radix-heavy; sidebar/panel/settings covered by focused tests and E2E.
        "src/components/layout/AppShell.tsx",
        "src/components/layout/ThemeLanguageMenu.tsx",
        // Sidebar is measured (has focused unit tests).
        // Hook tested directly; AppShell is excluded so keep hook out of the bundle gate.
        "src/components/layout/use-sidebar-shell.ts",
        // Move dialog is mostly UI glue; behavior is validated by chat flows + E2E.
        "src/features/chat/components/MoveConversationDialog.tsx",
        // Chat toolbar sheet: many Radix branches; chat/page + integration tests cover flows.
        "src/features/chat/components/ChatToolbarOverflowMenu.tsx",
        // Drag/drop + file input wiring; DocumentUploadZone tests cover behavior without stable branch ratios.
        "src/features/documents/components/DocumentUploadZone.tsx",
        // RAG settings form is measured (has smoke-level unit tests).
        "src/features/settings/components/MeCanonicalJsonPanels.tsx",
        // Settings admin sections mocked in RagConfigForm tests; covered via settings E2E.
        "src/features/settings/components/TaskLlmSettingsSection.tsx",
        "src/features/settings/components/AdvancedTaskModelSettingsForm.tsx",
        "src/features/settings/components/InternalPromptConfigurationSection.tsx",
        "src/features/settings/hooks/use-prompt-catalog.ts",
        "src/features/settings/components/UserAccountPreferencesSection.tsx",
        // Lab registry section is UI-heavy; validated via E2E/manual flows.
        "src/features/lab/components/classifier-registry-section.tsx",
        // Auth view wrappers are measured (has small unit tests).
        // Next.js route re-exports (logic tested on src/app/api/auth/*).
        "src/app/api/v5/**",
        // Page-level auth view; covered by E2E auth flows.
        "src/features/auth/components/ConfirmEmailView.tsx",
        // URL sync hooks: thin next/navigation glue covered by page/E2E tests.
        "src/features/projects/hooks/use-sync-active-project-from-url-param.ts",
        "src/features/chat/hooks/use-runtime-config-validate.ts",
        "src/features/chat/hooks/use-media-query.ts",
        "src/features/projects/hooks/use-active-project-snapshot.ts",
        // Banner is thin status glue next to the job panel (covered via panel/E2E).
        "src/features/lab/components/lab-background-job-banner.tsx",
        // Lab layout mount hook: invalidateQueries on mount (thin glue next to active jobs banner).
        "src/features/lab/components/lab-active-jobs-refetch-on-mount.tsx",
        // Deprecated re-export of config-field-copy (no runtime logic).
        "src/features/settings/lib/project-config-field-copy.ts",
        // Type-only trace barrels (no runtime logic).
        "src/features/trace/index.ts",
        "src/features/trace/trace-types.ts",
      ],
      // Gate: product unit-test bundle ≥81% (LAB closure target).
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
      // Vitest (Vite) ESM resolver sometimes requires the explicit `.js` entry for Next internals.
      "next/navigation": "next/navigation.js",
    },
  },
});

