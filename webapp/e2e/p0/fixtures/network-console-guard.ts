import { expect, type Page, type Response } from "@playwright/test";

export type NetworkConsoleGuardOptions = {
  /** Ignore console.error messages matching these patterns. */
  ignoreConsole?: RegExp[];
  /** Ignore failed responses whose URL matches these patterns. */
  ignoreFailedRequestUrl?: RegExp[];
};

const DEFAULT_IGNORE_CONSOLE = [
  /favicon\.ico/i,
  /ResizeObserver loop/i,
  /Failed to load resource.*404/i,
];

const DEFAULT_IGNORE_FAILED_URL = [
  /favicon\.ico/i,
  /\/_next\/static\/media\//i,
];

export type NetworkConsoleGuard = {
  assertClean: () => void;
  getViolations: () => { consoleErrors: string[]; failedResponses: string[] };
};

/** Collects console errors and HTTP >=500 responses; assert at end of test. */
export function attachNetworkConsoleGuard(
  page: Page,
  options?: NetworkConsoleGuardOptions,
): NetworkConsoleGuard {
  const ignoreConsole = [...DEFAULT_IGNORE_CONSOLE, ...(options?.ignoreConsole ?? [])];
  const ignoreFailedUrl = [...DEFAULT_IGNORE_FAILED_URL, ...(options?.ignoreFailedRequestUrl ?? [])];
  const consoleErrors: string[] = [];
  const failedResponses: string[] = [];

  page.on("pageerror", (err) => {
    consoleErrors.push(`pageerror: ${err.message}`);
  });

  page.on("console", (msg) => {
    if (msg.type() !== "error") {
      return;
    }
    const text = msg.text();
    if (ignoreConsole.some((re) => re.test(text))) {
      return;
    }
    consoleErrors.push(`console.error: ${text}`);
  });

  page.on("response", (res: Response) => {
    const status = res.status();
    if (status < 500) {
      return;
    }
    const url = res.url();
    if (ignoreFailedUrl.some((re) => re.test(url))) {
      return;
    }
    failedResponses.push(`${status} ${url}`);
  });

  return {
    getViolations: () => ({ consoleErrors: [...consoleErrors], failedResponses: [...failedResponses] }),
    assertClean: () => {
      const { consoleErrors: ce, failedResponses: fr } = { consoleErrors, failedResponses };
      expect(ce, `Unexpected console/page errors:\n${ce.join("\n")}`).toEqual([]);
      expect(fr, `Unexpected HTTP 5xx responses:\n${fr.join("\n")}`).toEqual([]);
    },
  };
}
