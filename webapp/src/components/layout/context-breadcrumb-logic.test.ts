import { describe, it, expect } from "vitest";
import { inferMainSection, settingsTabKeyFromPath } from "./context-breadcrumb-logic";

describe("inferMainSection", () => {
  it("detects chat section without duplicate locale segment", () => {
    expect(inferMainSection("/chat")).toBe("chat");
    expect(inferMainSection("/chat/")).toBe("chat");
  });

  it("detects settings nested routes", () => {
    expect(inferMainSection("/settings/user")).toBe("settings");
    expect(inferMainSection("/settings")).toBe("settings");
  });

  it("detects documents and lab", () => {
    expect(inferMainSection("/documents")).toBe("documents");
    expect(inferMainSection("/lab")).toBe("lab");
  });
});

describe("settingsTabKeyFromPath", () => {
  it("maps longest matching tab prefix first", () => {
    expect(settingsTabKeyFromPath("/settings/account")).toBe("tabAccount");
    expect(settingsTabKeyFromPath("/settings/user")).toBe("tabUser");
    expect(settingsTabKeyFromPath("/settings")).toBe("tabGeneral");
  });

  it("returns null for non-settings paths", () => {
    expect(settingsTabKeyFromPath("/projects")).toBe(null);
  });
});
