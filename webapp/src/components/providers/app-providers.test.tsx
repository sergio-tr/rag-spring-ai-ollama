import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AppProviders } from "./app-providers";

describe("AppProviders", () => {
  it("renders children inside theme and query clients", () => {
    render(
      <AppProviders>
        <span data-testid="child">ok</span>
      </AppProviders>,
    );
    expect(screen.getByTestId("child")).toHaveTextContent("ok");
  });
});
