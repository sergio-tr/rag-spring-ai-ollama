import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";
import React from "react";
import {
  formatConsoleArgs,
  isSuppressedTestConsoleNoise,
} from "@/test-utils/suppress-test-console-noise";

// React 19 + RTL: mark the test env so batched updates are attributed correctly.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const origConsoleError = console.error.bind(console);
const origConsoleWarn = console.warn.bind(console);

console.error = (...args: unknown[]) => {
  if (isSuppressedTestConsoleNoise(formatConsoleArgs(args))) return;
  origConsoleError(...args);
};

console.warn = (...args: unknown[]) => {
  if (isSuppressedTestConsoleNoise(formatConsoleArgs(args))) return;
  origConsoleWarn(...args);
};

// Prevent Next.js Link from attempting route prefetching in unit tests.
// Prefetch can trigger network calls against the default happy-dom origin (127.0.0.1:3000).
vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) =>
    React.createElement("a", { href }, children),
}));

// Prevent next-intl from importing Next.js navigation internals in Vitest DOM.
vi.mock("next-intl/navigation", () => ({
  createNavigation: () => ({
    Link: ({ href, children }: { href: string; children: React.ReactNode }) =>
      React.createElement("a", { href }, children),
    redirect: vi.fn(),
    usePathname: () => "/",
    useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
    getPathname: (args: { href: string }) => args.href,
  }),
}));

// next-intl navigation helpers import `next/navigation` (which is not available in Vitest DOM).
// Provide a minimal stub so `@/navigation` can be imported by client components in unit tests.
vi.mock("next/navigation", () => ({
  redirect: vi.fn(),
  notFound: vi.fn(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  usePathname: () => "/",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/navigation.js", () => ({
  redirect: vi.fn(),
  notFound: vi.fn(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  usePathname: () => "/",
  useSearchParams: () => new URLSearchParams(),
}));

// Silence accidental Next-prefetch network calls against the default DOM origin (127.0.0.1:3000).
// Block dev-backend origins (CI sets NEXT_PUBLIC_API_BASE_URL=http://localhost:9000) so Vitest never
// emits ECONNREFUSED AggregateError noise when a hook forgets to mock apiFetch.
const realFetch = globalThis.fetch?.bind(globalThis);
const BLOCKED_FETCH_ORIGIN = /^https?:\/\/(localhost|127\.0\.0\.1):9000(?:\/|$)/i;

function resolveFetchUrl(input: RequestInfo | URL): string {
  const raw = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
  return raw.startsWith("/") ? `http://127.0.0.1:3000${raw}` : raw;
}

if (realFetch) {
  vi.stubGlobal("fetch", async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = resolveFetchUrl(input);
    if (url.startsWith("http://127.0.0.1:3000") || url.startsWith("http://localhost:3000")) {
      return new Response("", { status: 204 });
    }
    if (BLOCKED_FETCH_ORIGIN.test(url)) {
      return Response.json({ error: "vitest_backend_unreachable" }, { status: 503 });
    }
    return realFetch(input as never, init);
  });
}

// next-themes reads matchMedia on mount; jsdom does not provide it.
Object.defineProperty(window, "matchMedia", {
  writable: true,
  configurable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Some Vitest DOM environments do not provide sessionStorage/localStorage by default.
// Our runtime code guards storage access with try/catch; tests expect a working storage in the happy path.
class MemoryStorage implements Storage {
  #m = new Map<string, string>();
  get length() {
    return this.#m.size;
  }
  clear(): void {
    this.#m.clear();
  }
  getItem(key: string): string | null {
    return this.#m.has(key) ? (this.#m.get(key) ?? null) : null;
  }
  key(index: number): string | null {
    return Array.from(this.#m.keys())[index] ?? null;
  }
  removeItem(key: string): void {
    this.#m.delete(key);
  }
  setItem(key: string, value: string): void {
    this.#m.set(String(key), String(value));
  }
}

function ensureSessionStorageWorks() {
  try {
    const s: Storage | undefined = (globalThis as unknown as { sessionStorage?: Storage }).sessionStorage;
    if (!s) throw new Error("missing");
    s.setItem("__vitest__", "1");
    const ok = s.getItem("__vitest__") === "1";
    s.removeItem("__vitest__");
    if (ok) return;
  } catch {
    // fall through to polyfill
  }

  const storage = new MemoryStorage();
  Object.defineProperty(globalThis, "sessionStorage", { value: storage, configurable: true });
  Object.defineProperty(window, "sessionStorage", { value: storage, configurable: true });
}

ensureSessionStorageWorks();

// Base UI ScrollArea schedules viewport measurement using Web Animations API (jsdom lacks it).
if (typeof Element.prototype.getAnimations !== "function") {
  Element.prototype.getAnimations = function mockGetAnimations() {
    return [];
  };
}
