import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { AppearanceLanguagePanel } from "./AppearanceLanguagePanel";

const setTheme = vi.fn();
const replace = vi.fn();

vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: "light", setTheme }),
}));

vi.mock("@/navigation", () => ({
  usePathname: () => "/settings",
  useRouter: () => ({ replace }),
}));

describe("AppearanceLanguagePanel", () => {
  beforeEach(() => {
    setTheme.mockClear();
    replace.mockClear();
  });

  it("changes theme via next-themes", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <AppearanceLanguagePanel />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /dark/i }));
    expect(setTheme).toHaveBeenCalledWith("dark");
  });

  it("switches locale via router.replace", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="en">
        <AppearanceLanguagePanel />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /^Español$/ }));
    expect(replace).toHaveBeenCalledWith("/settings", { locale: "es" });
  });
});
