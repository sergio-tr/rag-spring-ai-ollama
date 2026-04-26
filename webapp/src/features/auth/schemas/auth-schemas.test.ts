import { describe, expect, it } from "vitest";
import { loginSchema, registerSchema } from "./auth-schemas";

describe("loginSchema", () => {
  it("accepts valid credentials", () => {
    const r = loginSchema.safeParse({ email: "a@b.co", password: "dev" });
    expect(r.success).toBe(true);
  });

  it("rejects empty password", () => {
    const r = loginSchema.safeParse({ email: "a@b.co", password: "" });
    expect(r.success).toBe(false);
  });
});

describe("registerSchema", () => {
  it("accepts valid payload", () => {
    const r = registerSchema.safeParse({
      name: "N",
      email: "a@b.co",
      password: "12345678",
    });
    expect(r.success).toBe(true);
  });
});
