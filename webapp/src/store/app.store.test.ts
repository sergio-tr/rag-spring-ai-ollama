import { beforeEach, describe, expect, it } from "vitest";
import { useAppStore } from "./app.store";

describe("useAppStore", () => {
  beforeEach(() => {
    useAppStore.setState({ activeProject: null });
  });

  it("updates active project", () => {
    useAppStore.getState().setActiveProject({ id: "p1", name: "Alpha" });
    expect(useAppStore.getState().activeProject).toEqual({
      id: "p1",
      name: "Alpha",
    });
  });
});
