import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ChatConfigTechnicalDetails, CompactSummaryRow } from "./chat-config-compact-ui";

describe("chat-config-compact-ui", () => {
  it("renders technical details collapsed by default", () => {
    render(
      <ChatConfigTechnicalDetails summary="Advanced technical details">
        <p>Inner content</p>
      </ChatConfigTechnicalDetails>,
    );
    const details = screen.getByTestId("chat-config-technical-details");
    expect(details).not.toHaveAttribute("open");
    expect(screen.getByText("Advanced technical details")).toBeInTheDocument();
  });

  it("can render technical details open by default", () => {
    render(
      <ChatConfigTechnicalDetails summary="Open details" defaultOpen>
        <p>Visible</p>
      </ChatConfigTechnicalDetails>,
    );
    expect(screen.getByTestId("chat-config-technical-details")).toHaveAttribute("open");
  });

  it("renders compact summary rows", () => {
    render(<CompactSummaryRow label="Model" value="llama" testId="compact-model" />);
    expect(screen.getByTestId("compact-model")).toHaveTextContent("Model");
    expect(screen.getByTestId("compact-model")).toHaveTextContent("llama");
  });
});
