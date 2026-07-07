import { registeredModelNameError } from "@/features/lab/lib/registered-model-validation";
import { describe, expect, it } from "vitest";

describe("registeredModelNameError", () => {
  it('rejects reserved name "default"', () => {
    expect(registeredModelNameError("default")).toBe("reserved");
    expect(registeredModelNameError("DEFAULT")).toBe("reserved");
  });
});
