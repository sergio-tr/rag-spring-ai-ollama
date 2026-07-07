import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { useLocalizedPath } from "./use-localized-path";

describe("useLocalizedPath", () => {
  it("prefixes paths with the active locale from next-intl", () => {
    const { result } = renderHook(() => useLocalizedPath(), {
      wrapper: ({ children }) => <IntlTestProvider locale="en">{children}</IntlTestProvider>,
    });
    expect(result.current("/settings/user")).toBe("/en/settings/user");
  });

  it("uses the Spanish locale when the provider locale is es", () => {
    const { result } = renderHook(() => useLocalizedPath(), {
      wrapper: ({ children }) => <IntlTestProvider locale="es">{children}</IntlTestProvider>,
    });
    expect(result.current("/settings/user")).toBe("/es/settings/user");
  });
});
