import type { QueryClient } from "@tanstack/react-query";

let registeredQueryClient: QueryClient | null = null;

/** Called once from `AppProviders` so session reset works outside React hooks. */
export function registerAppQueryClient(client: QueryClient): void {
  registeredQueryClient = client;
}

export function getRegisteredAppQueryClient(): QueryClient | null {
  return registeredQueryClient;
}

export function resolveAppQueryClient(preferred?: QueryClient): QueryClient | null {
  return preferred ?? registeredQueryClient;
}
