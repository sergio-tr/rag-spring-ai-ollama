import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { ProjectIcon, ProjectVisual } from "./ProjectVisual";

describe("ProjectVisual", () => {
  it("renders fallback icon and fallback color when values are missing", () => {
    const { container } = render(<ProjectVisual />);
    const dot = container.querySelector("span");
    expect(dot).toBeTruthy();
    expect(dot?.getAttribute("style")).toContain("background-color: #9ca3af");
  });

  it("renders color dot for valid hex color", () => {
    const { container } = render(<ProjectVisual colorHex="#ff0000" />);
    const dot = container.querySelector("span");
    expect(dot?.getAttribute("style")).toContain("background-color: #ff0000");
  });

  it.each(["folder", "briefcase", "star", "code", "rocket", "shield", "chat", "lab", "book", "other"])(
    "renders icon variant for %s",
    (iconKey) => {
      const { container } = render(<ProjectIcon iconKey={iconKey} className="icon" />);
      expect(container.querySelector("svg.icon")).toBeTruthy();
    },
  );
});
