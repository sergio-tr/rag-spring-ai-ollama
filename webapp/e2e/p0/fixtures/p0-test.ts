import { test as base } from "@playwright/test";
import { attachNetworkConsoleGuard, type NetworkConsoleGuardOptions } from "./network-console-guard";

type P0Fixtures = {
  networkGuard: ReturnType<typeof attachNetworkConsoleGuard>;
};

export const test = base.extend<P0Fixtures>({
  networkGuard: [
    async ({ page }, use) => {
      const guard = attachNetworkConsoleGuard(page);
      await use(guard);
      guard.assertClean();
    },
    { auto: true },
  ],
});

export { expect } from "@playwright/test";

export function testWithGuardOptions(options: NetworkConsoleGuardOptions) {
  return base.extend<P0Fixtures>({
    networkGuard: [
      async ({ page }, use) => {
        const guard = attachNetworkConsoleGuard(page, options);
        await use(guard);
        guard.assertClean();
      },
      { auto: true },
    ],
  });
}
