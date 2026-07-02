import { describe, it, expect } from "vitest";
import { ProjectCreateError } from "./project-create-errors";

describe("project-create-errors", () => {
  it("exposes failure kind on ProjectCreateError", () => {
    const err = new ProjectCreateError("CREATE_FAILED");
    expect(err.kind).toBe("CREATE_FAILED");
    expect(err.name).toBe("ProjectCreateError");
  });
});
