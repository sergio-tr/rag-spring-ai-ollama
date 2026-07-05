/**
 * Known-noise patterns from RTL/React 19 and partial RQ mocks in unit tests.
 * Used by vitest.setup and vitest.config onConsoleLog - not for production.
 */
export const SUPPRESSED_TEST_CONSOLE_PATTERNS: RegExp[] = [
  /not wrapped in act\(\.\.\.\)/i,
  /An update to .+ inside a test was not wrapped in act/i,
  /Query data cannot be undefined/i,
  /Maximum call stack size exceeded/i,
  /ECONNREFUSED/i,
  /vitest_backend_unreachable/i,
];

export function formatConsoleArgs(args: unknown[]): string {
  return args
    .map((a) => {
      if (typeof a === "string") return a;
      if (a instanceof Error) return `${a.name}: ${a.message}\n${a.stack ?? ""}`;
      try {
        return JSON.stringify(a);
      } catch {
        return String(a);
      }
    })
    .join(" ");
}

/** Avoid RegExp OOM when matching multi-megabyte stack traces from poll loops. */
const MAX_CONSOLE_NOISE_SAMPLE_CHARS = 8_192;

export function isSuppressedTestConsoleNoise(message: string): boolean {
  const sample =
    message.length > MAX_CONSOLE_NOISE_SAMPLE_CHARS
      ? message.slice(0, MAX_CONSOLE_NOISE_SAMPLE_CHARS)
      : message;
  return SUPPRESSED_TEST_CONSOLE_PATTERNS.some((re) => re.test(sample));
}
