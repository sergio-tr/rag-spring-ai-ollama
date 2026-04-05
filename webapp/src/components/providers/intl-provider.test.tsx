import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import en from "../../../messages/en.json";
import { IntlProviderClient } from "./intl-provider";

describe("IntlProviderClient", () => {
  it("renders children with messages and trims empty timeZone to UTC", () => {
    render(
      <IntlProviderClient locale="en" messages={en} timeZone="  " nowMs={1_700_000_000_000}>
        <span>ok</span>
      </IntlProviderClient>,
    );
    expect(screen.getByText("ok")).toBeInTheDocument();
  });
});
