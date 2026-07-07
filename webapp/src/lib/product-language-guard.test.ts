import { existsSync, readdirSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import en from "../../messages/en.json";
import es from "../../messages/es.json";
import {
  findForbiddenInText,
  formatViolations,
  readUtf8,
  relPath,
  scanMarkdownProse,
  scanMessagesJson,
  scanTypeScriptSource,
  walkFiles,
} from "./product-language-guard";

const __dirname = dirname(fileURLToPath(import.meta.url));
const webappRoot = join(__dirname, "../..");
const repoRoot = join(webappRoot, "..");

function evidenceClosureDirs(): string[] {
  const prefixes = [
    "assistant-configuration-closure",
  ];
  const roots = [
    join(webappRoot, "test-fixtures"),
  ];
  const dirs: string[] = [];
  for (const evidenceRoot of roots) {
    if (!existsSync(evidenceRoot)) {
      continue;
    }
    for (const name of readdirSync(evidenceRoot)) {
      if (!prefixes.some((prefix) => name.startsWith(prefix))) {
        continue;
      }
      const path = join(evidenceRoot, name);
      if (statSync(path).isDirectory()) {
        dirs.push(path);
      }
    }
  }
  return dirs;
}

function walkMarkdownFiles(rootDir: string): string[] {
  return walkFiles(rootDir, { extensions: new Set([".md"]), skipTestFiles: false });
}

const MARKDOWN_EVIDENCE_AUDIT_SUFFIXES = [
  "PRODUCT_LANGUAGE_AUDIT.md",
  "COMMANDS_USED.md",
  "FILE_INSPECTION_NOTES.md",
  "LANGUAGE_AUDIT.md",
  "TECHNICAL_DETAILS_VISIBILITY_REPORT.md",
];

function isEvidenceAuditMarkdown(relativePath: string): boolean {
  return MARKDOWN_EVIDENCE_AUDIT_SUFFIXES.some((name) => relativePath.endsWith(name));
}

describe("product language guard", () => {
  it("detects forbidden phrases in plain text", () => {
    const hits = findForbiddenInText("The profile hash is shown to users.");
    expect(hits.some((h) => h.patternId === "profile hash")).toBe(true);
  });

  it("allows markdown policy lines that document prohibitions", () => {
    const md = "Normal UI must not show `profile hash` in compact summary.";
    const violations = scanMarkdownProse("policy.md", md);
    expect(violations).toHaveLength(0);
  });

  it("flags markdown prose that positively exposes forbidden copy", () => {
    const md = "Select the Demo_Best preset in the configuration panel.";
    const violations = scanMarkdownProse("bad.md", md);
    expect(violations.some((v) => v.patternId === "Demo_Best")).toBe(true);
  });

  it("does not scan TypeScript identifiers without string literals", () => {
    const source = "const runtimeOverride = state.runtimeOverride;";
    expect(scanTypeScriptSource("features/chat/example.ts", source)).toHaveLength(0);
  });

  it("flags user-facing string literals in source", () => {
    const source = 'return <p>Your profile hash is {hash}</p>;';
    const violations = scanTypeScriptSource("features/chat/Bad.tsx", source);
    expect(violations.some((v) => v.patternId === "profile hash")).toBe(true);
  });
});

describe("product language guard - messages", () => {
  it("en.json product namespaces avoid forbidden visible copy", () => {
    const violations = scanMessagesJson("messages/en.json", en);
    expect(violations, formatViolations(violations)).toHaveLength(0);
  });

  it("es.json product namespaces avoid forbidden visible copy", () => {
    const violations = scanMessagesJson("messages/es.json", es);
    expect(violations, formatViolations(violations)).toHaveLength(0);
  });
});

describe("product language guard - app and features source", () => {
  const scanRoots = [
    join(webappRoot, "src/app"),
    join(webappRoot, "src/features"),
  ];

  for (const root of scanRoots) {
    it(`${relPath(webappRoot, root)} has no forbidden copy in user-facing string literals`, () => {
      const files = walkFiles(root, {
        extensions: new Set([".ts", ".tsx"]),
        skipTestFiles: true,
      });
      const violations = files.flatMap((file) => {
        const rel = relPath(join(webappRoot, "src"), file);
        const source = readUtf8(file);
        return scanTypeScriptSource(rel, source);
      });
      expect(violations, formatViolations(violations)).toHaveLength(0);
    });
  }
});

describe("product language guard - thesis evidence markdown", () => {
  const dirs = evidenceClosureDirs();

  it("finds at least one assistant-configuration evidence directory", () => {
    expect(dirs.length).toBeGreaterThan(0);
  });

  for (const dir of dirs) {
    it(`${relPath(repoRoot, dir)} thesis-facing markdown avoids forbidden positive copy`, () => {
      const files = walkMarkdownFiles(dir).filter((file) => !isEvidenceAuditMarkdown(relPath(repoRoot, file)));
      const violations = files.flatMap((file) => {
        const rel = relPath(repoRoot, file);
        return scanMarkdownProse(rel, readUtf8(file));
      });
      expect(violations, formatViolations(violations)).toHaveLength(0);
    });
  }
});
