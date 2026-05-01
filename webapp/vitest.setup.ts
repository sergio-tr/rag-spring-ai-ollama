import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";

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
