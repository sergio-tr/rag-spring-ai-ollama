import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { getAppTimeZone } from "./timezone";

describe("getAppTimeZone", () => {
  const prev = process.env.NEXT_PUBLIC_TIMEZONE;

  afterEach(() => {
    if (prev === undefined) delete process.env.NEXT_PUBLIC_TIMEZONE;
    else process.env.NEXT_PUBLIC_TIMEZONE = prev;
  });

  it("returns UTC when env is unset or empty", () => {
    delete process.env.NEXT_PUBLIC_TIMEZONE;
    expect(getAppTimeZone()).toBe("UTC");
    process.env.NEXT_PUBLIC_TIMEZONE = "   ";
    expect(getAppTimeZone()).toBe("UTC");
  });

  it("returns trimmed timezone when set", () => {
    process.env.NEXT_PUBLIC_TIMEZONE = "  Europe/Madrid  ";
    expect(getAppTimeZone()).toBe("Europe/Madrid");
  });
});
