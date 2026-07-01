import { describe, expect, it } from "vitest";
import { isLabComparisonAvailabilityBlocked } from "@/features/lab/lib/lab-comparison-availability";

describe("lab-comparison-availability", () => {
  it("allows single-model evaluation when one model is available", () => {
    expect(isLabComparisonAvailabilityBlocked(0, 1)).toBe(false);
    expect(isLabComparisonAvailabilityBlocked(1, 1)).toBe(false);
  });

  it("blocks only when comparison selection exceeds catalog availability", () => {
    expect(isLabComparisonAvailabilityBlocked(2, 1)).toBe(true);
    expect(isLabComparisonAvailabilityBlocked(3, 2)).toBe(true);
    expect(isLabComparisonAvailabilityBlocked(2, 2)).toBe(false);
  });

  it("does not block when catalog is empty (handled elsewhere)", () => {
    expect(isLabComparisonAvailabilityBlocked(2, 0)).toBe(false);
  });
});
