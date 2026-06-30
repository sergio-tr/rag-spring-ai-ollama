import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, extname } from "node:path";
import { FORBIDDEN_PRODUCT_VISIBLE_PATTERNS } from "./forbidden-product-visible-patterns";

export { FORBIDDEN_PRODUCT_VISIBLE_PATTERNS } from "./forbidden-product-visible-patterns";

export type ProductLanguageViolation = {
  file: string;
  line: number;
  patternId: string;
  excerpt: string;
};

const TEST_FILE_RE = /\.(test|spec)\.(ts|tsx)$/;
const SOURCE_SCAN_EXTENSIONS = new Set([".ts", ".tsx"]);

/** Mapping / sanitization modules may reference internal preset ids in string literals. */
const SOURCE_SCAN_SKIP_RELATIVE = new Set([
  "lib/product-preset-labels.ts",
  "features/presets/lib/preset-display.ts",
  "features/chat/lib/conversation-preset-ui.ts",
  "features/chat/lib/runtime-validation-copy.ts",
  "lib/forbidden-primary-ui-strings.ts",
  "lib/forbidden-product-visible-patterns.ts",
  "lib/product-language-guard.ts",
  "lib/user-facing-error-messages.ts",
]);

const MESSAGE_PRODUCT_ROOT_KEYS = new Set([
  "Metadata",
  "Nav",
  "Home",
  "Auth",
  "Chat",
  "Settings",
  "Lab",
  "Admin",
  "Projects",
  "Documents",
  "Tips",
]);

const MARKDOWN_POLICY_LINE =
  /\b(must\s+(\*\*)?not|do not|never show|forbidden|prohibited|avoid exposing|not expose|hidden by default|not visible|not show|no longer|replaced|confined to|only inside|allowed here|negative context|guard|scan product|check these|assert.*not|absent from|such as)\b/i;

const STRING_LITERAL_RE = /(["'`])((?:\\.|(?!\1).)*)\1/g;
const JSX_TEXT_RE = />([^<]+)</g;

export function forbiddenTermOnlyInBackticks(line: string, matchedText: string): boolean {
  const withoutBackticks = line.replace(/`[^`]*`/g, "");
  return !withoutBackticks.toLowerCase().includes(matchedText.toLowerCase());
}

export function isAllowedMarkdownPolicyLine(line: string, matchedText: string): boolean {
  if (MARKDOWN_POLICY_LINE.test(line)) {
    return true;
  }
  if (/\*\*no\*\*/i.test(line)) {
    return true;
  }
  if (line.includes("→") || /^\s*\|/.test(line)) {
    return true;
  }
  if (/\([^)]*profile hash[^)]*\)/i.test(line)) {
    return true;
  }
  if (line.includes("grep") || forbiddenTermOnlyInBackticks(line, matchedText)) {
    return true;
  }
  return false;
}

export function findForbiddenInText(
  text: string,
  options?: { lineFilter?: (line: string, lineNo: number, match: string, patternId: string) => boolean },
): ProductLanguageViolation[] {
  const violations: ProductLanguageViolation[] = [];
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]!;
    const lineNo = i + 1;
    for (const { id, pattern } of FORBIDDEN_PRODUCT_VISIBLE_PATTERNS) {
      const match = line.match(pattern);
      if (!match) {
        continue;
      }
      if (options?.lineFilter?.(line, lineNo, match[0]!, id) === false) {
        continue;
      }
      violations.push({
        file: "",
        line: lineNo,
        patternId: id,
        excerpt: line.trim().slice(0, 200),
      });
    }
  }
  return violations;
}

export function stripMarkdownCodeFences(markdown: string): string {
  return markdown.replace(/```[\s\S]*?```/g, "");
}

export function scanMarkdownProse(relativePath: string, markdown: string): ProductLanguageViolation[] {
  const prose = stripMarkdownCodeFences(markdown);
  const violations = findForbiddenInText(prose, {
    lineFilter: (line, _lineNo, match, patternId) => {
      if (isAllowedMarkdownPolicyLine(line, match)) {
        return false;
      }
      return true;
    },
  });
  return violations.map((v) => ({ ...v, file: relativePath }));
}

function stripLineComment(line: string): string {
  return line.replace(/\/\/.*$/, "");
}

export function scanTypeScriptUserFacingLine(line: string): string[] {
  const cleaned = stripLineComment(line);
  const segments: string[] = [];
  for (const match of cleaned.matchAll(STRING_LITERAL_RE)) {
    segments.push(match[2]!);
  }
  for (const match of cleaned.matchAll(JSX_TEXT_RE)) {
    const raw = match[1]!;
    for (const part of raw.split(/[{]/)) {
      const text = part.replace(/[}].*$/, "").trim();
      if (text) {
        segments.push(text);
      }
    }
  }
  return segments;
}

export function scanTypeScriptSource(relativePath: string, source: string): ProductLanguageViolation[] {
  if (SOURCE_SCAN_SKIP_RELATIVE.has(relativePath.replace(/\\/g, "/"))) {
    return [];
  }
  const violations: ProductLanguageViolation[] = [];
  const lines = source.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const segments = scanTypeScriptUserFacingLine(lines[i]!);
    for (const segment of segments) {
      for (const { id, pattern } of FORBIDDEN_PRODUCT_VISIBLE_PATTERNS) {
        if (pattern.test(segment)) {
          violations.push({
            file: relativePath,
            line: i + 1,
            patternId: id,
            excerpt: lines[i]!.trim().slice(0, 200),
          });
        }
      }
    }
  }
  return violations;
}

export function collectStringValues(value: unknown, out: string[] = []): string[] {
  if (typeof value === "string") {
    out.push(value);
    return out;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      collectStringValues(item, out);
    }
    return out;
  }
  if (value && typeof value === "object") {
    for (const child of Object.values(value as Record<string, unknown>)) {
      collectStringValues(child, out);
    }
  }
  return out;
}

export function scanMessagesJson(relativePath: string, json: unknown): ProductLanguageViolation[] {
  const violations: ProductLanguageViolation[] = [];
  const root = json as Record<string, unknown>;
  for (const [namespace, section] of Object.entries(root)) {
    if (!MESSAGE_PRODUCT_ROOT_KEYS.has(namespace)) {
      continue;
    }
    const values = collectStringValues(section);
    for (const value of values) {
      for (const { id, pattern } of FORBIDDEN_PRODUCT_VISIBLE_PATTERNS) {
        if (pattern.test(value)) {
          violations.push({
            file: `${relativePath}#${namespace}`,
            line: 0,
            patternId: id,
            excerpt: value.slice(0, 200),
          });
        }
      }
    }
  }
  return violations;
}

export function walkFiles(rootDir: string, options: {
  extensions: Set<string>;
  skipTestFiles?: boolean;
}): string[] {
  const out: string[] = [];
  function walk(current: string) {
    for (const entry of readdirSync(current)) {
      const full = join(current, entry);
      const st = statSync(full);
      if (st.isDirectory()) {
        if (entry === "node_modules" || entry === ".next") {
          continue;
        }
        walk(full);
        continue;
      }
      const ext = extname(entry);
      if (!options.extensions.has(ext)) {
        continue;
      }
      if (options.skipTestFiles && TEST_FILE_RE.test(entry)) {
        continue;
      }
      out.push(full);
    }
  }
  walk(rootDir);
  return out;
}

export function formatViolations(violations: ProductLanguageViolation[]): string {
  return violations
    .map((v) => `${v.file}:${v.line} [${v.patternId}] ${v.excerpt}`)
    .join("\n");
}

export function readUtf8(path: string): string {
  return readFileSync(path, "utf8");
}

export function relPath(root: string, file: string): string {
  return relative(root, file).replace(/\\/g, "/");
}
