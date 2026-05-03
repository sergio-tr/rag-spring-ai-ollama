import { describe, it, expect, vi } from "vitest";
import { IntlErrorCode } from "use-intl/core";

vi.mock("next-intl/server", () => ({
  getRequestConfig:
    (cb: (p: { requestLocale: Promise<string | undefined> }) => unknown) =>
    async (p: { requestLocale: Promise<string | undefined> }) => await cb(p),
}));

import request from "./request";

describe("i18n request config", () => {
  it("resolves locale, messages, and timeZone", async () => {
    const log = vi.spyOn(console, "error").mockImplementation(() => {});
    const cfg = await request({
      requestLocale: Promise.resolve("es"),
    });
    expect(cfg.locale).toBe("es");
    expect(cfg.timeZone).toBeDefined();
    expect(cfg.messages).toBeDefined();
    expect(typeof cfg.onError).toBe("function");
    cfg.onError?.({ code: IntlErrorCode.ENVIRONMENT_FALLBACK } as Parameters<NonNullable<typeof cfg.onError>>[0]);
    cfg.onError?.({ code: IntlErrorCode.INVALID_MESSAGE } as Parameters<NonNullable<typeof cfg.onError>>[0]);
    log.mockRestore();
  });

  it("falls back to default locale for invalid requestLocale", async () => {
    const cfg = await request({
      requestLocale: Promise.resolve("xx"),
    });
    expect(cfg.locale).toBe("en");
  });
});
