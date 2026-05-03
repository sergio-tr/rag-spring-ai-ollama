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
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
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

  it("can switch to light and system themes", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <AppearanceLanguagePanel />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /light/i }));
    expect(setTheme).toHaveBeenCalledWith("light");
    await user.click(screen.getByRole("button", { name: /system/i }));
    expect(setTheme).toHaveBeenCalledWith("system");
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

  it("switches back to English when current locale is Spanish", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="es">
        <AppearanceLanguagePanel />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /^English$/ }));
    expect(replace).toHaveBeenCalledWith("/settings", { locale: "en" });
  });
});
