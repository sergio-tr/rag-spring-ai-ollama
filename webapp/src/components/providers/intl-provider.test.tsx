import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import en from "../../../messages/en.json";
import { IntlErrorCode } from "use-intl/core";
import { IntlProviderClient, onIntlError } from "./intl-provider";

describe("IntlProviderClient", () => {
  it("renders children with messages and trims empty timeZone to UTC", () => {
    render(
      <IntlProviderClient locale="en" messages={en} timeZone="  " nowMs={1_700_000_000_000}>
        <span>ok</span>
      </IntlProviderClient>,
    );
    expect(screen.getByText("ok")).toBeInTheDocument();
  });

  it("filters ENVIRONMENT_FALLBACK errors", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    onIntlError({ code: IntlErrorCode.ENVIRONMENT_FALLBACK } as never);
    expect(spy).not.toHaveBeenCalled();
  });

  it("logs non-ENVIRONMENT_FALLBACK errors", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const err = { code: IntlErrorCode.MISSING_MESSAGE, message: "x" } as never;
    onIntlError(err);
    expect(spy).toHaveBeenCalledWith(err);
  });
});
