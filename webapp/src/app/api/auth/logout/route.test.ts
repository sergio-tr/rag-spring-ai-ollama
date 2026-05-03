import { describe, it, expect } from "vitest";
import { POST } from "./route";

describe("POST logout route", () => {
  it("clears auth cookies", async () => {
    const res = await POST();
    expect(res.status).toBe(200);
    const data = (await res.json()) as { ok: boolean };
    expect(data.ok).toBe(true);
  });
});
