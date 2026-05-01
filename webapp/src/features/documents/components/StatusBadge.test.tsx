import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("maps status to badge variants", () => {
    const { rerender } = render(<StatusBadge status="READY" />);
    expect(screen.getByText("READY")).toBeInTheDocument();
    rerender(<StatusBadge status="ERROR" />);
    expect(screen.getByText("ERROR")).toBeInTheDocument();
    rerender(<StatusBadge status="INGESTING" />);
    expect(screen.getByText("INGESTING")).toBeInTheDocument();
  });
});
