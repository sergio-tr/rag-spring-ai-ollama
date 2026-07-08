import { test } from "@playwright/test";

/** Prefix for environment-not-ready outcomes (distinct from functional test failures). */
export const BLOCKED_ENVIRONMENT_PREFIX = "BLOCKED_ENVIRONMENT";

export class BlockedEnvironmentError extends Error {
  constructor(detail: string) {
    super(`${BLOCKED_ENVIRONMENT_PREFIX}: ${detail}`);
    this.name = "BlockedEnvironmentError";
  }
}

export function isBlockedEnvironmentMessage(message: string): boolean {
  return message.includes(BLOCKED_ENVIRONMENT_PREFIX);
}

/** Marks the current test as skipped (blocked env), rethrows other errors. */
export function skipIfBlockedEnvironment(error: unknown): never | void {
  const message = error instanceof Error ? error.message : String(error);
  if (error instanceof BlockedEnvironmentError || isBlockedEnvironmentMessage(message)) {
    test.skip(true, message);
    return;
  }
  throw error;
}
