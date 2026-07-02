import { describe, expect, it } from "vitest";
import {
  isReservedRegisteredModelName,
  registeredModelNameError,
} from "@/features/lab/lib/registered-model-validation";

describe("RegisteredModelFormValidation", () => {
  it("rejects reserved name default", () => {
    expect(isReservedRegisteredModelName("default")).toBe(true);
    expect(registeredModelNameError("default")).toBe("reserved");
  });

  it("rejects reserved names case-insensitively", () => {
    expect(registeredModelNameError("NONE")).toBe("reserved");
  });

  it("accepts valid unique application names", () => {
    expect(registeredModelNameError("acta-classifier-v1")).toBeNull();
  });
});
