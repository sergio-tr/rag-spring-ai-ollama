import { QueryClient } from "@tanstack/react-query";
import { beforeEach, describe, expect, it } from "vitest";
import {
  getRegisteredAppQueryClient,
  registerAppQueryClient,
  resolveAppQueryClient,
} from "@/lib/query-client-registry";

describe("query-client-registry", () => {
  beforeEach(() => {
    registerAppQueryClient(new QueryClient());
  });

  it("registerAppQueryClient stores and returns the client", () => {
    const client = new QueryClient();
    registerAppQueryClient(client);
    expect(getRegisteredAppQueryClient()).toBe(client);
  });

  it("resolveAppQueryClient prefers explicit client over registered", () => {
    const registered = new QueryClient();
    const preferred = new QueryClient();
    registerAppQueryClient(registered);
    expect(resolveAppQueryClient(preferred)).toBe(preferred);
    expect(resolveAppQueryClient()).toBe(registered);
    expect(resolveAppQueryClient(undefined)).toBe(registered);
  });
});
