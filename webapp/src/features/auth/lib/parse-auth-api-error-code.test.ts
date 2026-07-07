import { describe, it, expect } from "vitest";
import { ApiError } from "@/lib/api-client";
import { parseAuthApiErrorCode } from "./parse-auth-api-error-code";

describe("parseAuthApiErrorCode", () => {
  it("returns undefined for non-ApiError", () => {
    expect(parseAuthApiErrorCode(new Error("x"))).toBeUndefined();
    expect(parseAuthApiErrorCode(null)).toBeUndefined();
  });

  it("parses code from rawBodyPreview JSON", () => {
    const err = new ApiError(400, "Bad Request", {
      kind: "http",
      rawBodyPreview: JSON.stringify({ code: "RESET_TOKEN_EXPIRED", message: "expired" }),
    });
    expect(parseAuthApiErrorCode(err)).toBe("RESET_TOKEN_EXPIRED");
  });

  it("returns undefined when preview is not valid JSON", () => {
    const err = new ApiError(400, "Bad Request", {
      kind: "http",
      rawBodyPreview: "not-json",
    });
    expect(parseAuthApiErrorCode(err)).toBeUndefined();
  });

  it("returns undefined when code is missing or not a string", () => {
    const missingCode = new ApiError(400, "Bad Request", {
      kind: "http",
      rawBodyPreview: JSON.stringify({ message: "only message" }),
    });
    expect(parseAuthApiErrorCode(missingCode)).toBeUndefined();

    const numericCode = new ApiError(400, "Bad Request", {
      kind: "http",
      rawBodyPreview: JSON.stringify({ code: 42 }),
    });
    expect(parseAuthApiErrorCode(numericCode)).toBeUndefined();
  });
});
