import { describe, it, expect } from "vitest";
import { ApiError } from "@/lib/api-client";
import {
  findProjectByName,
  isReconcilablePostFailure,
} from "./project-create-reconciliation";

describe("project-create-reconciliation", () => {
  it("treats network and gateway errors as reconcilable", () => {
    expect(isReconcilablePostFailure(new ApiError(0, "net", { kind: "network" }))).toBe(true);
    expect(isReconcilablePostFailure(new ApiError(503, "down"))).toBe(true);
    expect(isReconcilablePostFailure(new ApiError(409, "conflict"))).toBe(true);
    expect(isReconcilablePostFailure(new ApiError(400, "bad"))).toBe(false);
  });

  it("findProjectByName matches trimmed names", () => {
    const items = [
      { id: "a", name: "  Alpha  ", docCount: 0, convCount: 0, updatedAt: "" },
      { id: "b", name: "Beta", docCount: 0, convCount: 0, updatedAt: "" },
    ];
    expect(findProjectByName(items, "Alpha")?.id).toBe("a");
    expect(findProjectByName(items, "Gamma")).toBeNull();
  });
});
