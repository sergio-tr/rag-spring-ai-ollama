import { describe, expect, it } from "vitest";

import { createTraceparent } from "./traceparent";

describe("createTraceparent", () => {
  it("matches W3C traceparent shape", () => {
    const tp = createTraceparent();
    const parts = tp.split("-");
    expect(parts).toHaveLength(4);
    expect(parts[0]).toBe("00");
    expect(parts[1]).toHaveLength(32);
    expect(parts[2]).toHaveLength(16);
    expect(parts[3]).toBe("01");
    expect(/^[0-9a-f]+$/.test(parts[1]!)).toBe(true);
    expect(/^[0-9a-f]+$/.test(parts[2]!)).toBe(true);
  });
});
