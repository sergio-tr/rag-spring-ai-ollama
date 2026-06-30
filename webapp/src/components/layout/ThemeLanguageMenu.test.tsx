import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { ThemeLanguageMenu } from "./ThemeLanguageMenu";

vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: "system", setTheme: vi.fn() }),
}));

const replace = vi.fn();
const push = vi.fn();

vi.mock("@/navigation", () => ({
  usePathname: () => "/projects",
  useRouter: () => ({ replace, push }),
}));

describe("ThemeLanguageMenu", () => {
  beforeEach(() => {
    replace.mockClear();
    push.mockClear();
  });

  it("navigates to locale-neutral /settings so next-intl resolves /en/settings (not /en/en/settings)", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="en">
        <ThemeLanguageMenu />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /^settings$/i }));
    await user.click(screen.getByRole("menuitem", { name: /^settings$/i }));
    expect(push).toHaveBeenCalledWith("/settings");
  });
});
