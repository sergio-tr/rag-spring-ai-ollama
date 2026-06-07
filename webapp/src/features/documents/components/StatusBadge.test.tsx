import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("maps status to human-readable labels", () => {
    const { rerender } = render(
      <IntlTestProvider>
        <StatusBadge status="READY" />
      </IntlTestProvider>,
    );
    expect(screen.getByText("Ready")).toBeInTheDocument();
    rerender(
      <IntlTestProvider>
        <StatusBadge status="ERROR" />
      </IntlTestProvider>,
    );
    expect(screen.getByText("Failed")).toBeInTheDocument();
    rerender(
      <IntlTestProvider>
        <StatusBadge status="INGESTING" />
      </IntlTestProvider>,
    );
    expect(screen.getByText("Processing…")).toBeInTheDocument();
  });
});
