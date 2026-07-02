import { describe, it, expect } from "vitest";
import { projectCreateWarningMessage } from "./project-create-feedback";

describe("project-create-feedback", () => {
  const t = (key: string) => key;

  it("prioritizes config warning over activate warning", () => {
    const msg = projectCreateWarningMessage(
      {
        project: { id: "p", name: "N", docCount: 0, convCount: 0, updatedAt: "" },
        configSaveFailed: true,
        activateFailed: true,
      },
      t,
    );
    expect(msg).toBe("createConfigWarning");
  });

  it("returns refresh warning when only refresh failed", () => {
    const msg = projectCreateWarningMessage(
      {
        project: { id: "p", name: "N", docCount: 0, convCount: 0, updatedAt: "" },
        refreshFailed: true,
      },
      t,
    );
    expect(msg).toBe("createRefreshWarning");
  });
});
