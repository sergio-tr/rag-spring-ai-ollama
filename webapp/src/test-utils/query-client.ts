import { QueryClient } from "@tanstack/react-query";

/** QueryClient tuned for unit tests (no retries, no stale cache surprises). */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
      mutations: { retry: false },
    },
  });
}
