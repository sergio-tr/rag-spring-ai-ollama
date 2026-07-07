import { describe, it, expect } from "vitest";
import { projectCreateWarningMessage } from "./project-create-feedback";

describe("project-create-feedback", () => {
  const t = (key: string) => key;
  const baseProject = { id: "p", name: "N", docCount: 0, convCount: 0, updatedAt: "" };

  it("returns activate warning when activate failed", () => {
    const msg = projectCreateWarningMessage({ project: baseProject, activateFailed: true }, t);
    expect(msg).toBe("createActivateWarning");
  });

  it("returns refresh warning when only refresh failed", () => {
    const msg = projectCreateWarningMessage({ project: baseProject, refreshFailed: true }, t);
    expect(msg).toBe("createRefreshWarning");
  });

  it("returns reconciled warning when response was incomplete but reconciled", () => {
    const msg = projectCreateWarningMessage({ project: baseProject, responseIncomplete: true }, t);
    expect(msg).toBe("createReconciledWarning");
  });

  it("returns null for a clean success outcome", () => {
    const msg = projectCreateWarningMessage({ project: baseProject }, t);
    expect(msg).toBeNull();
  });
});
